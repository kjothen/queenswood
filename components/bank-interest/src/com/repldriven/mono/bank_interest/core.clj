(ns com.repldriven.mono.bank-interest.core
  (:require
    [com.repldriven.mono.bank-interest.domain :as domain]
    [com.repldriven.mono.bank-interest.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     products]
    [com.repldriven.mono.bank-chart-of-accounts.interface :as
     chart-of-accounts]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]))

(def ^:private gl-code-interest-payable "2400")

(defn- customer-accounts
  [accounts]
  (filter #(and (not= :product-type-internal
                      (:product-type %))
                (not= :product-type-settlement
                      (:product-type %))
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

(defn- accrue-account
  [config interest-payable-id account as-of-date]
  (let [{:keys [bank-id account-id currency]} account]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [{:keys [interest-rate-bps]} (get-product-version
                                       config
                                       bank-id
                                       account)
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
                        (domain/accrual-transaction interest-payable-id
                                                    account-id
                                                    currency
                                                    whole-units
                                                    as-of-date))
          _ (when transaction
              (let-nom>
                [expanded-legs (chart-of-accounts/expand-legs
                                txn
                                bank-id
                                (:legs transaction))
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
  [config interest-payable-id account as-of-date]
  (let [{:keys [bank-id account-id currency]} account]
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
                       interest-payable-id
                       account-id
                       currency
                       balance
                       as-of-date)
          _
          (when transaction
            (let-nom>
              [expanded-legs (chart-of-accounts/expand-legs
                              txn
                              bank-id
                              (:legs transaction))
               transaction+legs (transactions/record-transaction
                                 txn
                                 (assoc transaction :legs expanded-legs))
               _ (balances/apply-legs txn
                                      (:legs transaction+legs)
                                      (:transaction-type transaction+legs))]))])))))

(defn- get-interest-payable-account
  [config bank-id]
  (let [result (cash-accounts/get-account-by-gl-code
                config
                bank-id
                gl-code-interest-payable)]
    (when-not (error/anomaly? result) result)))

(defn- process-customer-accounts
  [config bank-id interest-payable-id as-of-date f]
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
                                 interest-payable-id
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

(defn accrue-daily
  [config data]
  (let [{:keys [bank-id as-of-date]} data]
    (if-let [interest-payable (get-interest-payable-account config bank-id)]
      (let [processed (process-customer-accounts
                       config
                       bank-id
                       (:account-id interest-payable)
                       as-of-date
                       accrue-account)]
        (if (error/anomaly? processed)
          processed
          {:bank-id bank-id
           :as-of-date as-of-date
           :accounts-processed processed}))
      (error/reject :interest/no-interest-payable-account
                    {:message
                     (str "Bank has no 2400 interest-payable account"
                          " in its chart of accounts")
                     :bank-id bank-id}))))

(defn capitalize-monthly
  [config data]
  (let [{:keys [bank-id as-of-date]} data]
    (if-let [interest-payable (get-interest-payable-account config bank-id)]
      (let [processed (process-customer-accounts
                       config
                       bank-id
                       (:account-id interest-payable)
                       as-of-date
                       capitalize-account)]
        (if (error/anomaly? processed)
          processed
          {:bank-id bank-id
           :as-of-date as-of-date
           :accounts-processed processed}))
      (error/reject :interest/no-interest-payable-account
                    {:message
                     (str "Bank has no 2400 interest-payable account"
                          " in its chart of accounts")
                     :bank-id bank-id}))))
