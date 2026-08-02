(ns com.repldriven.queenswood.interest.core
  (:require
    [com.repldriven.queenswood.interest.domain :as domain]
    [com.repldriven.queenswood.interest.store :as store]
    [com.repldriven.queenswood.balance.interface :as balances]
    [com.repldriven.queenswood.cash-account-product-query.interface :as
     products]
    [com.repldriven.queenswood.cash-account-query.interface :as cash-accounts]
    [com.repldriven.queenswood.ledger-account.interface :as ledger-accounts]
    [com.repldriven.queenswood.policy.interface :as policy]
    [com.repldriven.queenswood.transaction.interface :as transactions]
    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def ^:private customer-product-types
  "Product types whose cash-accounts should earn interest. GL accounts
  carry nil :product-type and are excluded by this set membership
  check."
  #{:product-type-sub-ledger-current :product-type-sub-ledger-savings
    :product-type-sub-ledger-term-deposit})

(defn- customer-account?
  [account]
  (and (contains? customer-product-types (:product-type account))
       (= :cash-account-status-opened (:account-status account))))

(def ^:private product-cache (cache/create 60000))

(defn- get-product-version
  [config bank-id account]
  (cache/lookup product-cache
                [(:product-id account) (:version-id account)]
                #(products/get-version config
                                       bank-id
                                       (:product-id account)
                                       (:version-id account))))

(defn- tag-customer-legs
  "Stamp the customer `account-id` legs with `product-type` so they fan
  out to the right control: a default bucket into the deposit control
  (2100/2200/2300), an interest-accrued bucket into 2400. The GL expense
  leg carries a different account-id and posts directly."
  [legs account-id product-type]
  (mapv (fn [leg]
          (if (= (:account-id leg) account-id)
            (assoc leg :product-type product-type)
            leg))
        legs))

(defn- accrue-account
  "One account's share of the day's interest, posted silently: the
  customer's interest-accrued bucket is credited and the sub-unit
  remainder carried, with no transaction record and no ledger leg.

  Accrual is not a statement line — what a customer sees is
  capitalisation — so the per-account transaction bought nothing and
  cost the two GL rows every accrual in the bank had to read and write.
  The bank's side is posted once for the run."
  [config txn account balances _as-of-date]
  (let [{:keys [bank-id account-id currency product-type]} account]
    (let-nom>
      [version (get-product-version config bank-id account)
       interest-rate-bps (:interest-rate-bps version)
       ;; The balance comes off the enumeration, not a read inside this
       ;; transaction. It is the figure the run computed on, and
       ;; recording it beside the amount is what makes the accrual
       ;; reproducible after the fact.
       balance (domain/bucket balances
                              :balance-type-default
                              currency
                              :balance-status-posted)
       {:keys [whole-units carry]} (domain/daily-interest balance
                                                          interest-rate-bps)
       ;; Guard on the VALUE, not on whole-units being present:
       ;; daily-interest can return :whole-units 0 with a carry, and 0
       ;; is truthy. A zero accrual still records its row.
       _ (when (and whole-units (not (zero? whole-units)))
           (balances/apply-legs txn
                                [(domain/accrual-leg account-id
                                                     product-type
                                                     currency
                                                     whole-units)]
                                :transaction-type-interest-accrual))
       _ (when carry
           (balances/set-carry txn
                               account-id
                               :balance-type-default currency
                               :balance-status-posted carry))]
      ;; What the row records. The balance alone would not reproduce
      ;; the amount — the carry feeds the same calculation.
      {:amount (or whole-units 0)
       :input-balance (domain/net-balance balance)
       :input-carry (:credit-carry balance)})))

(defn- capitalize-account
  [_config txn account balances as-of-date]
  (let [{:keys [bank-id account-id currency product-type]} account]
    (let-nom>
      [balance (domain/bucket balances
                              :balance-type-interest-accrued
                              currency
                              :balance-status-posted)
       transaction (domain/capitalization-transaction account-id
                                                      currency
                                                      balance
                                                      as-of-date)
       _ (when transaction
           (let-nom>
             [expanded-legs (ledger-accounts/add-control-legs
                             txn
                             bank-id
                             (tag-customer-legs (:legs transaction)
                                                account-id
                                                product-type))
              transaction+legs (transactions/record-transaction
                                txn
                                (assoc transaction :legs expanded-legs))
              _ (balances/apply-legs txn
                                     (:legs transaction+legs)
                                     (:transaction-type transaction+legs))]))]
      ;; Capitalisation sweeps whatever accrued, so the amount and the
      ;; input are the same figure.
      (let [accrued (domain/net-balance balance)]
        {:amount accrued :input-balance accrued}))))

(defn- get-interest-expense-account
  [config bank-id]
  (let [result (ledger-accounts/find-by-code config
                                             bank-id
                                             :gl-account-code-interest-expense)]
    (when-not (error/anomaly? result) result)))

(defn- post-run-entry
  "The bank's ledger entry for one currency of an accrual run, posted
  once at close. The total comes off the SUM index rather than a tally
  the pass kept, because a resumed run only posts what it processed
  itself while the index covers every row whichever attempt wrote it.

  `record-and-post` reads back on a duplicate idempotency key, so
  reaching close twice posts once."
  [config ctx interest-expense-id currency]
  (let [{:keys [bank-id business-day account-kind]} ctx]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [total (store/sum-account-run-amounts txn
                                               bank-id
                                               business-day
                                               account-kind
                                               currency)
          payable (ledger-accounts/find-by-code
                   txn
                   bank-id
                   :gl-account-code-interest-payable)
          transaction (domain/accrual-run-transaction
                       interest-expense-id
                       (:ledger-account-id payable)
                       bank-id
                       currency
                       total
                       business-day)
          _ (when transaction
              (transactions/record-and-post txn transaction))])))))

(defn- post-run-entries
  [config ctx interest-expense-id currencies]
  (reduce (fn [_ currency]
            (let [result (post-run-entry config
                                         ctx
                                         interest-expense-id
                                         currency)]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          currencies))

(defn- process-account
  "Row lifecycle around one account's posting. Skips an account a prior
  attempt already finished, otherwise posts and flips the row to DONE in
  the same transaction, so the work and the record of the work commit
  together. Returns :done, :skipped, or an anomaly."
  [config ctx f account balances]
  (let [{:keys [bank-id business-day account-kind]} ctx
        account-id (:account-id account)]
    (store/transact
     config
     (fn [txn]
       (let [row (store/load-account-run txn
                                         bank-id
                                         business-day
                                         account-kind
                                         account-id)]
         (if (and row (not (domain/pending? row)))
           :skipped
           (let-nom> [result (f config txn account balances business-day)
                      _
                      (store/save-account-run txn
                                              (domain/account-run-done
                                               (or row
                                                   (domain/new-account-run
                                                    bank-id
                                                    business-day
                                                    account-kind
                                                    account-id
                                                    (:currency account)))
                                               result))]
             :done)))))))

(defn- mark-account-failed
  "Records a failed account in its own transaction — the posting's
  transaction has already rolled back, taking any DONE flip with it."
  [config ctx account anomaly]
  (let [{:keys [bank-id business-day account-kind]} ctx
        account-id (:account-id account)]
    (store/transact config
                    (fn [txn]
                      (let [row (or (store/load-account-run txn
                                                            bank-id
                                                            business-day
                                                            account-kind
                                                            account-id)
                                    (domain/new-account-run
                                     bank-id
                                     business-day
                                     account-kind
                                     account-id
                                     (:currency account)))]
                        (store/save-account-run txn
                                                (domain/account-run-failed
                                                 row
                                                 (error/kind anomaly))))))))

(defn- enumerate-account
  "Writes the account's PENDING row if it has none. Reads before
  writing: a resumed run must not knock a finished account back to
  PENDING."
  [config ctx account]
  (let [{:keys [bank-id business-day account-kind]} ctx
        {:keys [account-id currency]} account]
    (store/transact
     config
     (fn [txn]
       (when-not (store/load-account-run txn
                                         bank-id
                                         business-day
                                         account-kind
                                         account-id)
         (store/save-account-run txn
                                 (domain/new-account-run bank-id
                                                         business-day
                                                         account-kind
                                                         account-id
                                                         currency)))))))

(defn- process-customer-accounts
  "Streams the bank's accounts with their balances, recording scope as
  it goes and posting each customer account. A failing account is
  marked FAILED and the pass continues — aborting would strand every
  later account as PENDING and the run would never close.

  One merged scan pairs each account with its balances, so the posting
  transaction does no balance read of its own and computes on the
  figure the pass streamed in."
  [config ctx f]
  (cash-accounts/reduce-accounts-with-balances
   config
   (:bank-id ctx)
   (fn [tally {:keys [account balances]}]
     (if-not (customer-account? account)
       tally
       (let [enumerated (enumerate-account config ctx account)]
         (if (error/anomaly? enumerated)
           enumerated
           (let [result (process-account config ctx f account balances)
                 ;; Every currency in scope, gathered as the pass runs.
                 ;; Complete even on a resumed run, because the pass
                 ;; visits every account each time and only the posting
                 ;; is skipped — so the ledger entries at close cover
                 ;; currencies an earlier attempt already accrued.
                 tally (update tally :currencies conj (:currency account))]
             (if (error/anomaly? result)
               (do (mark-account-failed config ctx account result)
                   (update tally :failed inc))
               (update tally result inc)))))))
   {:done 0 :skipped 0 :failed 0 :currencies #{}}))

(defn- run-interest
  "Run an interest pass under the platform daily-count limit for
  `policy-kind`. Counts prior runs of this kind for the org on
  `as-of-date` and rejects if the limit is reached, then enumerates the
  account set into InterestAccountRun rows and posts each one.

  The InterestRun record is written only once enumeration has finished,
  so a crash part-way leaves no run record and the daily limit does not
  block the retry. The PENDING rows already written are what make that
  retry a resumption rather than a restart. A run that finished with
  failures still closes; its residue is the count of FAILED rows, which
  `run-progress` reports.

  Each account is its own short FDB transaction — no long transaction
  is held across the run. An accrual run then posts the bank's side
  once per currency, which is the other half of a double entry the
  per-account postings deliberately leave open."
  [config data spec]
  (let [{:keys [policy-kind run-kind account-fn run-entry?]} spec
        {:keys [bank-id as-of-date]} data
        ctx (assoc spec
                   :bank-id bank-id
                   :business-day as-of-date)]
    (if-let [interest-expense (get-interest-expense-account config bank-id)]
      (let-nom>
        [policies (policy/get-effective-policies config {:bank-id bank-id})
         today-count (store/count-by-org-business-day-per-kind config
                                                               bank-id
                                                               run-kind
                                                               as-of-date)
         aggregates {policy-kind {#{:bank-id :business-day} today-count}}
         _ (domain/check-daily-count policies policy-kind aggregates)
         tally (process-customer-accounts config ctx account-fn)
         _ (when run-entry?
             (post-run-entries config
                               ctx
                               (:ledger-account-id interest-expense)
                               (:currencies tally)))
         run (domain/close-interest-run (domain/new-interest-run bank-id
                                                                 as-of-date
                                                                 run-kind))
         _ (store/save-run config run)]
        {:bank-id bank-id
         :as-of-date as-of-date
         :accounts-processed (+ (:done tally) (:skipped tally))
         :accounts-failed (:failed tally)
         :run-state (:state run)})
      (error/reject :interest/no-interest-expense-account
                    {:message (str "Bank has no 5100 interest-expense account"
                                   " in its chart of accounts")
                     :bank-id bank-id}))))

(def ^:private accrual
  {:policy-kind :accrual
   :run-kind :interest-run-kind-accrue
   :account-kind :interest-account-run-kind-accrue
   :account-fn accrue-account
   ;; Only accrual leaves its ledger side to the run. Capitalisation
   ;; still posts a transaction per account, because that one is the
   ;; customer's statement line.
   :run-entry? true})

(def ^:private capitalization
  {:policy-kind :capitalize
   :run-kind :interest-run-kind-capitalize
   :account-kind :interest-account-run-kind-capitalize
   :account-fn capitalize-account})

(defn accrue-day [config data] (run-interest config data accrual))

(defn capitalize-accrued
  [config data]
  (run-interest config data capitalization))

(def ^:private kind->run {:accrue accrual :capitalize capitalization})

(defn run-progress
  [config bank-id as-of-date kind]
  (if-let [{:keys [run-kind account-kind]} (kind->run kind)]
    (let-nom>
      [scope (store/count-account-runs config bank-id as-of-date account-kind)
       done (store/count-account-runs-by-state
             config
             bank-id
             as-of-date
             account-kind
             :interest-account-run-state-done)
       failed (store/count-account-runs-by-state
               config
               bank-id
               as-of-date
               account-kind
               :interest-account-run-state-failed)
       run (store/load-run config bank-id as-of-date run-kind)]
      {:scope scope
       :done done
       :failed failed
       :pending (- scope done failed)
       :run-state (:state run)})
    (error/reject :interest/unknown-run-kind
                  {:message "Run kind must be :accrue or :capitalize"
                   :kind kind})))
