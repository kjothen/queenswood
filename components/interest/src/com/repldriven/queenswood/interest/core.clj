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
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]))

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

(defn- get-product-version
  "The account's pinned product version, memoised for the run.

  The map is built from the accounts as they stream rather than from
  the bank's current products: an account pins `product-id` and
  `version-id` when it opens, and a pinned version may be one the bank
  no longer offers, so enumerating what is on offer would miss
  accounts. A bank has dozens of versions in use rather than millions,
  so after the first few accounts this is a hash lookup.

  Scoped to the run rather than a TTL cache, because a pass wants one
  view of the rates it is applying — an entry expiring mid-pass would
  accrue two halves of the same bank at two different rates."
  [config versions bank-id account]
  (let [k [(:product-id account) (:version-id account)]]
    (if-some [hit (get @versions k)]
      hit
      (let [version (products/get-version config
                                          bank-id
                                          (:product-id account)
                                          (:version-id account))]
        (when-not (error/anomaly? version)
          (swap! versions assoc k version))
        version))))

(defn- accrue-account
  "One account's share of the day's interest, posted silently: the
  customer's interest-accrued bucket is credited and the sub-unit
  remainder carried, with no transaction record and no ledger leg.

  Accrual is not a statement line — what a customer sees is
  capitalisation — so the per-account transaction bought nothing and
  cost the two GL rows every accrual in the bank had to read and write.
  The bank's side is posted once for the run.

  Everything it computes on was frozen by the scan, so the whole
  account costs one unread write. That is sound because only this pass
  and capitalisation ever write an interest-accrued bucket; the
  principal it reads sits on the default bucket, which payments move
  and which this never writes."
  [config ctx txn account balances]
  (let [{:keys [bank-id account-id currency]} account]
    (let-nom>
      [version (get-product-version config (:versions ctx) bank-id account)
       ;; Everything below comes off the scan, not a read inside this
       ;; transaction. These are the figures the run computed on, and
       ;; recording them beside the amount is what makes the accrual
       ;; reproducible after the fact.
       accrued (domain/bucket balances
                              :balance-type-interest-accrued
                              currency
                              :balance-status-posted)
       net (domain/principal balances currency)
       carry (:credit-carry accrued 0)
       day (domain/daily-interest net carry (:interest-rate-bps version))
       _ (cond
          ;; A zero rate. Nothing earned and nothing to write.
          (nil? day)
          nil

          ;; One write, and only the accrued bucket. Written even at
          ;; :whole-units 0, because the carry still moved.
          accrued
          (balances/accrue txn accrued (:whole-units day) (:carry day))

          ;; Nowhere to put it. The account is still recorded as done
          ;; at zero — the pass has no business failing an account
          ;; over a bucket the product should have opened for it.
          :else
          (log/warnf (str "Account %s has a non-zero interest rate but no"
                          " accrual balance - interest is not being accrued"
                          " for this account.")
                     account-id))]
      {:amount (if (and day accrued) (:whole-units day) 0)
       :input-balance net
       :input-carry carry})))

(defn- capitalize-account
  "One account's accrued interest swept into its spendable balance.

  Keeps its per-account transaction, because that transaction is the
  customer's statement line — the one thing about interest they
  actually see. What it drops is the control legs: fanning out per
  account made every capitalisation in the bank read and write the
  2400 payable and the deposit control, the same two-row contention
  accrual was taken off. The bank's side is posted once per currency
  and product type at close.

  Unlike accrual this cannot be an unread write. It credits the default
  bucket, which payments move, so `apply-legs` reads inside the posting
  transaction and the read-modify-write there is load-bearing."
  [_config ctx txn account balances]
  (let [{:keys [account-id currency]} account]
    (let-nom>
      [balance (domain/bucket balances
                              :balance-type-interest-accrued
                              currency
                              :balance-status-posted)
       transaction (domain/capitalization-transaction account-id
                                                      currency
                                                      balance
                                                      (:business-day ctx))
       _ (when transaction
           (let-nom>
             [recorded (transactions/record-transaction txn transaction)
              _ (balances/apply-legs txn
                                     (:bank-id account)
                                     (:legs recorded)
                                     (:transaction-type recorded))]))]
      ;; Capitalisation sweeps whatever accrued, so the amount and the
      ;; input are the same figure.
      (let [accrued (domain/net-balance balance)]
        {:amount accrued :input-balance accrued}))))

(defn- find-gl-account
  "A bank's ledger account for a chart role, as an id. Rejects with the
  interest brick's own kind rather than the ledger brick's, because a
  bank missing a control account the run has to post to is a chart of
  accounts problem the run cannot work around."
  [txn bank-id gl-account-code]
  (let [result (ledger-accounts/find-by-code txn bank-id gl-account-code)]
    (if (error/anomaly? result)
      (error/reject :interest/missing-gl-account
                    {:message "Bank has no such account in its chart"
                     :bank-id bank-id
                     :gl-account-code gl-account-code})
      (:ledger-account-id result))))

(defn- accrual-gl-accounts
  "Resolved before the pass runs, not at close. The accrual credits
  customer balances as it goes, and its ledger side is the other half
  of that entry — so a bank whose chart cannot take the posting should
  fail before the books go out rather than after."
  [config bank-id]
  (let-nom>
    [expense (find-gl-account config bank-id :gl-account-code-interest-expense)
     payable (find-gl-account config bank-id :gl-account-code-interest-payable)]
    {:expense expense :payable payable}))

(defn- capitalization-gl-accounts
  "Interest payable plus every deposit control a customer product could
  roll into. All four are resolved up front for the same reason accrual
  resolves two: the per-account transactions have already moved money
  by the time close is reached."
  [config bank-id]
  (let-nom>
    [payable (find-gl-account config bank-id :gl-account-code-interest-payable)
     current (find-gl-account config
                              bank-id
                              :gl-account-code-customer-deposits-current)
     savings (find-gl-account config
                              bank-id
                              :gl-account-code-customer-deposits-savings)
     term (find-gl-account config
                           bank-id
                           :gl-account-code-customer-deposits-term)]
    {:payable payable
     :controls {:product-type-sub-ledger-current current
                :product-type-sub-ledger-savings savings
                :product-type-sub-ledger-term-deposit term}}))

(defn- post-accrual-entry
  "The bank's ledger entry for one currency of an accrual run, posted
  once at close. The total comes off the SUM index rather than a tally
  the pass kept, because a resumed run only posts what it processed
  itself while the index covers every row whichever attempt wrote it.

  `record-and-post` reads back on a duplicate idempotency key, so
  reaching close twice posts once."
  [config ctx gl currency]
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
          transaction (domain/accrual-run-transaction (:expense gl)
                                                      (:payable gl)
                                                      bank-id
                                                      currency
                                                      total
                                                      business-day)
          _ (when transaction
              (transactions/record-and-post txn bank-id transaction))])))))

(defn- post-capitalization-entry
  "The bank's ledger entry for one currency and product type of a
  capitalisation run. Split by product type because the credit side is
  whichever deposit control that product rolls into, so one entry per
  currency could not name them all and still balance."
  [config ctx gl [currency product-type]]
  (let [{:keys [bank-id business-day account-kind]} ctx]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [total (store/sum-account-run-amounts-by-product
                 txn
                 bank-id
                 business-day
                 account-kind
                 currency
                 product-type)
          transaction (domain/capitalization-run-transaction
                       (:payable gl)
                       (get-in gl [:controls product-type])
                       bank-id
                       currency
                       product-type
                       total
                       business-day)
          _ (when transaction
              (transactions/record-and-post txn bank-id transaction))])))))

(defn- post-run-entries
  "Posts the bank's side once the accounts are done, one entry per group
  the pass saw. Short-circuits on the first anomaly — the remaining
  groups would post into the same broken chart."
  [entry-fn config ctx gl groups]
  (reduce (fn [_ group]
            (let [result (entry-fn config ctx gl group)]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          groups))

(def ^:private chunk-size
  "Accounts posted per transaction. FDB caps a transaction at 10MB and
  five seconds; an accrual writes two small records per account, so
  this sits well inside both while spreading the per-transaction cost
  over a hundred accounts instead of paying it for each."
  100)

(defn- post-account
  "One account's posting and its row, inside the chunk's transaction.
  Skips an account an earlier attempt already finished. Returns :done,
  :skipped, or an anomaly."
  [config ctx txn account balances]
  (let [{:keys [bank-id business-day account-kind account-fn]} ctx
        {:keys [account-id currency product-type]} account
        row (store/load-account-run txn
                                    bank-id
                                    business-day
                                    account-kind
                                    account-id)]
    (if (and row (not (domain/pending? row)))
      :skipped
      (let-nom> [result (account-fn config ctx txn account balances)
                 _ (store/save-account-run txn
                                           (domain/account-run-done
                                            (or row
                                                (domain/new-account-run
                                                 bank-id
                                                 business-day
                                                 account-kind
                                                 account-id
                                                 currency
                                                 product-type))
                                            result))]
        :done))))

(defn- post-chunk
  "Posts a chunk of accounts in one transaction, so every balance write
  and every row flip in it commits together or none does. Returns the
  per-state counts, or an anomaly if any account in the chunk failed —
  in which case nothing in the chunk landed."
  [config ctx chunk]
  (store/transact
   config
   (fn [txn]
     (reduce (fn [counts [account balances]]
               (let [result (post-account config ctx txn account balances)]
                 (if (error/anomaly? result)
                   (reduced result)
                   (update counts result inc))))
             {:done 0 :skipped 0}
             chunk))))

(defn- mark-chunk-failed
  "Records every account in a failed chunk as FAILED, in its own
  transaction — the chunk's transaction has already rolled back, taking
  any DONE flip with it.

  The whole chunk is marked rather than the one account that raised.
  Accrual reads nothing and writes a row only this pass writes, so a
  failure here is a database that is unwell or a product whose accounts
  all fail the same way; isolating the offender would draw a
  distinction that does not exist in practice."
  [config ctx chunk anomaly]
  (let [{:keys [bank-id business-day account-kind]} ctx]
    (store/transact
     config
     (fn [txn]
       (reduce
        (fn [_ [account _balances]]
          (let [{:keys [account-id currency product-type]} account
                row (or (store/load-account-run txn
                                                bank-id
                                                business-day
                                                account-kind
                                                account-id)
                        (domain/new-account-run bank-id
                                                business-day
                                                account-kind
                                                account-id
                                                currency
                                                product-type))
                result (store/save-account-run txn
                                               (domain/account-run-failed
                                                row
                                                (error/kind anomaly)))]
            (when (error/anomaly? result) (reduced result))))
        nil
        chunk)))))

(defn- flush-chunk
  "Posts whatever the pass has accumulated and clears it. A failing
  chunk is marked FAILED and the pass carries on — aborting would leave
  every later account untouched and the run would never close."
  [config ctx state]
  (let [{:keys [chunk tally]} state]
    (if (empty? chunk)
      state
      (let [result (post-chunk config ctx chunk)]
        (assoc state
               :chunk []
               :tally (if (error/anomaly? result)
                        (do (mark-chunk-failed config ctx chunk result)
                            (update tally :failed + (count chunk)))
                        (-> tally
                            (update :done + (:done result))
                            (update :skipped + (:skipped result)))))))))

(defn- process-customer-accounts
  "Streams the bank's accounts with their balances and posts each
  customer account, a chunk of them per transaction.

  One merged scan pairs each account with its balances, so a posting
  reads nothing of its own and computes on the figures the pass
  streamed in. No row is written ahead of the work: an account is
  either done, in which case a re-run skips it, or it is not, in which
  case a re-run redoes it — and a row saying the pass intended to reach
  it distinguishes neither."
  [config ctx]
  (let-nom>
    [state (cash-accounts/reduce-accounts-with-balances
            config
            (:bank-id ctx)
            (fn [state {:keys [account balances]}]
              (if-not (customer-account? account)
                state
                (let [state
                      (-> state
                          (update :chunk conj [account balances])
                          ;; Every group in scope, gathered as the pass
                          ;; runs. Complete even on a re-run, because
                          ;; the pass visits every account each time and
                          ;; only the posting is skipped — so the ledger
                          ;; entries at close cover groups an earlier
                          ;; attempt posted. Accrual reads the currency
                          ;; alone off these; capitalisation needs the
                          ;; pair.
                          (update-in [:tally :groups]
                                     conj
                                     [(:currency account)
                                      (:product-type account)]))]
                  (if (< (count (:chunk state)) chunk-size)
                    state
                    (flush-chunk config ctx state)))))
            {:chunk []
             :tally {:done 0 :skipped 0 :failed 0 :groups #{}}})]
    (:tally (flush-chunk config ctx state))))

(defn- run-interest
  "Run an interest pass under the platform daily-count limit for
  `policy-kind`. Counts prior runs of this kind for the org on
  `as-of-date` and rejects if the limit is reached, then streams the
  bank's accounts and posts them a chunk at a time.

  The InterestRun record is written only once the pass has finished, so
  a crash part-way leaves no run record and the daily limit does not
  block the retry. What makes that retry safe is the DONE rows the
  chunks committed: a re-run streams every account again and skips the
  ones already posted. A run that finished with failures still closes;
  its residue is the count of FAILED rows, which `run-progress`
  reports.

  A chunk is a short FDB transaction — no long transaction is held
  across the run. Both kinds then post the bank's side at close, which
  is the other half of a double entry the per-account postings
  deliberately leave open: accrual once per currency, capitalisation
  once per currency and product type.

  The chart of accounts is resolved before any account is touched. Both
  kinds move customer money as they go, so a bank that cannot take the
  ledger side should fail before the books go out rather than after."
  [config data spec]
  (let [{:keys [policy-kind run-kind gl-fn entry-fn group-fn]} spec
        {:keys [bank-id as-of-date]} data
        ctx (assoc spec
                   :bank-id bank-id
                   :business-day as-of-date
                   ;; Run-scoped, so every account in the pass is
                   ;; accrued against the same view of the rates.
                   :versions (atom {}))]
    (let-nom>
      [gl (gl-fn config bank-id)
       policies (policy/get-effective-policies config {:bank-id bank-id})
       today-count (store/count-by-org-business-day-per-kind config
                                                             bank-id
                                                             run-kind
                                                             as-of-date)
       aggregates {policy-kind {#{:bank-id :business-day} today-count}}
       _ (domain/check-daily-count policies policy-kind aggregates)
       tally (process-customer-accounts config ctx)
       _ (post-run-entries entry-fn
                           config
                           ctx
                           gl
                           (group-fn (:groups tally)))
       run (domain/close-interest-run (domain/new-interest-run bank-id
                                                               as-of-date
                                                               run-kind))
       _ (store/save-run config run)]
      {:bank-id bank-id
       :as-of-date as-of-date
       :accounts-processed (+ (:done tally) (:skipped tally))
       :accounts-failed (:failed tally)
       :run-state (:state run)})))

(def ^:private accrual
  {:policy-kind :accrual
   :run-kind :interest-run-kind-accrue
   :account-kind :interest-account-run-kind-accrue
   :account-fn accrue-account
   :gl-fn accrual-gl-accounts
   :entry-fn post-accrual-entry
   ;; Both of accrual's aggregate legs are fixed GL accounts, so the
   ;; product type the pass collected is not a distinction it needs.
   :group-fn (fn [groups] (into #{} (map first) groups))})

(def ^:private capitalization
  {:policy-kind :capitalize
   :run-kind :interest-run-kind-capitalize
   :account-kind :interest-account-run-kind-capitalize
   :account-fn capitalize-account
   :gl-fn capitalization-gl-accounts
   :entry-fn post-capitalization-entry
   ;; The credit side is a different deposit control per product type,
   ;; so capitalisation posts against the pair the pass collected.
   :group-fn identity})

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
