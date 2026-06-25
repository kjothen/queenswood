(ns com.repldriven.mono.bank-interest.core
  (:require
    [com.repldriven.mono.bank-interest.domain :as domain]
    [com.repldriven.mono.bank-interest.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     products]
    [com.repldriven.mono.bank-ledger-account.interface :as
     ledger-accounts]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]))

(def ^:private customer-product-types
  "Product types whose cash-accounts should earn interest. GL accounts
  carry nil :product-type and are excluded by this set membership
  check."
  #{:product-type-sub-ledger-current :product-type-sub-ledger-savings
    :product-type-sub-ledger-term-deposit})

(defn- customer-accounts
  [accounts]
  (filter #(and (contains? customer-product-types (:product-type %))
                (= :cash-account-status-opened
                   (:account-status %)))
          accounts))

(def ^:private product-cache (cache/create 60000))

(defn- get-product-version
  [config bank-id account]
  (cache/lookup
   product-cache
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
  [config interest-expense-id account as-of-date]
  (let [{:keys [bank-id account-id currency product-type]} account]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [version (get-product-version config bank-id account)
          interest-rate-bps (:interest-rate-bps version)
          balance (balances/get-balance txn
                                        account-id
                                        :balance-type-default
                                        currency
                                        :balance-status-posted)
          {:keys [whole-units carry]} (domain/daily-interest
                                       balance
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
                                  :balance-type-default
                                  currency
                                  :balance-status-posted
                                  carry))])))))

(defn- capitalize-account
  [config _interest-expense-id account as-of-date]
  (let [{:keys [bank-id account-id currency product-type]} account]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [balance (balances/get-balance txn
                                        account-id
                                        :balance-type-interest-accrued
                                        currency
                                        :balance-status-posted)
          transaction (domain/capitalization-transaction
                       account-id
                       currency
                       balance
                       as-of-date)
          _
          (when transaction
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
                                      (:transaction-type transaction+legs))]))])))))

(defn- get-interest-expense-account
  [config bank-id]
  (let [result (ledger-accounts/find-by-code
                config
                bank-id
                :gl-account-code-interest-expense)]
    (when-not (error/anomaly? result) result)))

(defn- process-customer-accounts
  [config bank-id interest-expense-id as-of-date f]
  (loop [cursor nil
         n 0]
    (let [page (cash-accounts/get-accounts
                config
                bank-id
                (when cursor {:after cursor}))]
      (if (error/anomaly? page)
        page
        (let [processed
              (reduce
               (fn [n account]
                 (let [result (f config
                                 interest-expense-id
                                 account
                                 as-of-date)]
                   (if (error/anomaly? result)
                     (reduced result)
                     (inc n))))
               n
               (customer-accounts (:accounts page)))]
          (if (error/anomaly? processed)
            processed
            (if (:after page)
              (recur (:after page) processed)
              processed)))))))

(defn- run-interest
  "Run an interest pass (`account-fn` per customer account) under the
  platform daily-count limit for `kind`. Counts prior runs of this
  `status` for the org on `as-of-date`, rejects if the limit is
  reached, then processes accounts and records the run. The run record
  has primary key `[bank_id, business_day, status]`, so a duplicate
  save is idempotent — the count never exceeds the limit even if two
  runs race. Each step is its own short FDB transaction (no long
  transaction held across account processing)."
  [config data status kind account-fn]
  (let [{:keys [bank-id as-of-date]} data]
    (if-let [interest-expense (get-interest-expense-account config bank-id)]
      (let-nom>
        [policies (policy/get-effective-policies config {:bank-id bank-id})
         today-count (store/count-by-org-business-day-per-kind
                      config
                      bank-id
                      status
                      as-of-date)
         aggregates {kind {#{:bank-id :business-day} today-count}}
         _ (domain/check-daily-count policies kind aggregates)
         processed (process-customer-accounts
                    config
                    bank-id
                    (:ledger-account-id interest-expense)
                    as-of-date
                    account-fn)
         _ (store/save-run config
                           (domain/new-interest-run bank-id
                                                    as-of-date
                                                    status))]
        {:bank-id bank-id
         :as-of-date as-of-date
         :accounts-processed processed})
      (error/reject :interest/no-interest-expense-account
                    {:message
                     (str "Bank has no 5100 interest-expense account"
                          " in its chart of accounts")
                     :bank-id bank-id}))))

(defn accrue-daily
  [config data]
  (run-interest config data :interest-accrue-done :accrual accrue-account))

(defn capitalize-monthly
  [config data]
  (run-interest config
                data
                :interest-capitalize-done
                :capitalize
                capitalize-account))
