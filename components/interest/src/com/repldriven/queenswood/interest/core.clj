(ns com.repldriven.queenswood.interest.core
  (:require
    [com.repldriven.queenswood.interest.domain :as domain]
    [com.repldriven.queenswood.interest.store :as store]
    [com.repldriven.queenswood.balance-query.interface :as balances-query]
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

(defn- customer-accounts
  [accounts]
  (filter #(and (contains? customer-product-types (:product-type %))
                (= :cash-account-status-opened (:account-status %)))
          accounts))

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
  [config txn interest-expense-id account as-of-date]
  (let [{:keys [bank-id account-id currency product-type]} account]
    (let-nom>
      [version (get-product-version config bank-id account)
       interest-rate-bps (:interest-rate-bps version)
       balance (balances-query/get-balance txn
                                           account-id
                                           :balance-type-default
                                           currency
                                           :balance-status-posted)
       {:keys [whole-units carry]} (domain/daily-interest balance
                                                          interest-rate-bps)
       ;; Guard the save on transaction VALUE, not on whole-units:
       ;; daily-interest can return a map with :whole-units 0 (carry
       ;; only), and 0 is truthy. accrual-transaction returns nil in
       ;; that case. Mirror in capitalize-account.
       transaction (when whole-units
                     (domain/accrual-transaction interest-expense-id
                                                 account-id
                                                 currency
                                                 whole-units
                                                 as-of-date))
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
                                     (:transaction-type transaction+legs))]))
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
  [_config txn _interest-expense-id account as-of-date]
  (let [{:keys [bank-id account-id currency product-type]} account]
    (let-nom>
      [balance (balances-query/get-balance txn
                                           account-id
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

(defn- process-account
  "Row lifecycle around one account's posting. Skips an account a prior
  attempt already finished, otherwise posts and flips the row to DONE in
  the same transaction, so the work and the record of the work commit
  together. Returns :done, :skipped, or an anomaly."
  [config {:keys [bank-id business-day account-kind]} f interest-expense-id
   account]
  (let [account-id (:account-id account)]
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
           (let-nom> [result
                      (f config txn interest-expense-id account business-day)
                      _
                      (store/save-account-run txn
                                              (domain/account-run-done
                                               (or row
                                                   (domain/new-account-run
                                                    bank-id
                                                    business-day
                                                    account-kind
                                                    account-id))
                                               result))]
             :done)))))))

(defn- mark-account-failed
  "Records a failed account in its own transaction — the posting's
  transaction has already rolled back, taking any DONE flip with it."
  [config {:keys [bank-id business-day account-kind]} account-id anomaly]
  (store/transact config
                  (fn [txn]
                    (let [row (or (store/load-account-run txn
                                                          bank-id
                                                          business-day
                                                          account-kind
                                                          account-id)
                                  (domain/new-account-run bank-id
                                                          business-day
                                                          account-kind
                                                          account-id))]
                      (store/save-account-run txn
                                              (domain/account-run-failed
                                               row
                                               (error/kind anomaly)))))))

(defn- enumerate-page
  "Writes PENDING rows for a page of accounts in one transaction. Reads
  before writing: a resumed run must not knock a finished account back
  to PENDING."
  [config {:keys [bank-id business-day account-kind]} accounts]
  (store/transact config
                  (fn [txn]
                    (doseq [{:keys [account-id]} accounts]
                      (when-not (store/load-account-run txn
                                                        bank-id
                                                        business-day
                                                        account-kind
                                                        account-id)
                        (store/save-account-run txn
                                                (domain/new-account-run
                                                 bank-id
                                                 business-day
                                                 account-kind
                                                 account-id)))))))

(defn- process-customer-accounts
  "Pages the bank's customer accounts, recording scope as it goes and
  posting each account. A failing account is marked FAILED and
  enumeration continues — aborting would strand every later account as
  PENDING and the run would never close."
  [config ctx interest-expense-id f]
  (loop [cursor nil
         tally {:done 0 :skipped 0 :failed 0}]
    (let [page (cash-accounts/get-accounts config
                                           (:bank-id ctx)
                                           (when cursor {:after cursor}))]
      (if (error/anomaly? page)
        page
        (let [accounts (customer-accounts (:accounts page))
              enumerated (enumerate-page config ctx accounts)]
          (if (error/anomaly? enumerated)
            enumerated
            (let [tally
                  (reduce (fn [tally account]
                            (let [result (process-account config
                                                          ctx
                                                          f
                                                          interest-expense-id
                                                          account)]
                              (if (error/anomaly? result)
                                (do (mark-account-failed config
                                                         ctx
                                                         (:account-id account)
                                                         result)
                                    (update tally :failed inc))
                                (update tally result inc))))
                          tally
                          accounts)]
              (if (:after page) (recur (:after page) tally) tally))))))))

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
  is held across the run."
  [config data {:keys [policy-kind run-kind account-fn] :as ctx}]
  (let [{:keys [bank-id as-of-date]} data
        ctx (assoc ctx
                   :bank-id bank-id
                   :business-day as-of-date)]
    (if-let [interest-expense (get-interest-expense-account config bank-id)]
      (let-nom>
        [policies (policy/get-effective-policies config {:bank-id bank-id})
         today-count
         (store/count-by-org-business-day-per-kind config
                                                   bank-id
                                                   run-kind
                                                   as-of-date)
         aggregates
         {policy-kind {#{:bank-id :business-day} today-count}}
         _
         (domain/check-daily-count policies policy-kind aggregates)
         tally
         (process-customer-accounts config
                                    ctx
                                    (:ledger-account-id interest-expense)
                                    account-fn)
         run
         (domain/close-interest-run
          (domain/new-interest-run bank-id as-of-date run-kind))
         _
         (store/save-run config run)]
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
   :account-fn accrue-account})

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
       amount (store/sum-account-run-amounts config
                                             bank-id
                                             as-of-date
                                             account-kind)
       run (store/load-run config bank-id as-of-date run-kind)]
      {:scope scope
       :done done
       :failed failed
       :pending (- scope done failed)
       :amount amount
       :run-state (:state run)})
    (error/reject :interest/unknown-run-kind
                  {:message "Run kind must be :accrue or :capitalize"
                   :kind kind})))
