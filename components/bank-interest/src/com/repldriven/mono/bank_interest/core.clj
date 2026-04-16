(ns com.repldriven.mono.bank-interest.core
  (:require
    [com.repldriven.mono.bank-interest.domain :as domain]
    [com.repldriven.mono.bank-interest.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     products]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]))

(defn- customer-accounts
  [accounts]
  (filter #(and (not= :account-type-internal
                      (:account-type %))
                (not= :account-type-settlement
                      (:account-type %))
                (= :cash-account-status-opened
                   (:account-status %)))
          accounts))

(def ^:private product-cache (cache/create 60000))

(defn- get-product-version
  [config org-id account]
  (cache/lookup
   product-cache
   [(:product-id account) (:version-id account)]
   #(products/get-version config
                          org-id
                          (:product-id account)
                          (:version-id account))))

(defn- accrue-account
  "Accrues daily interest for a single account. Reads the
  default/posted balance, calculates interest with carry,
  conditionally records an accrual transaction, updates carry. 
  All in one FDB transaction."
  [config settlement-id account as-of-date]
  (let [{:keys [organization-id account-id currency]} account]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [{:keys [interest-rate-bps]} (get-product-version
                                       config
                                       organization-id
                                       account)
          balance (balances/get-balance txn
                                        account-id
                                        :balance-type-default
                                        currency
                                        :balance-status-posted)
          {:keys [whole-units carry]} (domain/daily-interest
                                       balance
                                       interest-rate-bps)
          _ (when whole-units
              (let-nom>
                [transaction (domain/accrual-transaction settlement-id
                                                         account-id
                                                         currency
                                                         whole-units
                                                         as-of-date)
                 transaction+legs (transactions/record-transaction txn
                                                                   transaction)
                 _ (balances/apply-legs txn (:legs transaction+legs))]))
          _ (when carry
              (balances/set-carry txn
                                  account-id
                                  :balance-type-default
                                  currency
                                  :balance-status-posted
                                  carry))])))))

(defn- capitalize-account
  [config settlement-id account as-of-date]
  (let [{:keys [account-id currency]} account]
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
                       settlement-id
                       account-id
                       currency
                       balance
                       as-of-date)
          _ (when transaction
              (let-nom>
                [transaction+legs (transactions/record-transaction txn
                                                                   transaction)
                 _ (balances/apply-legs txn (:legs transaction+legs))]))])))))

(defn- get-settlement-account
  [config organization-id]
  (let [result (cash-accounts/get-account-by-type
                config
                organization-id
                :account-type-settlement)]
    (when-not (error/anomaly? result) result)))

(defn- process-customer-accounts
  [config organization-id settlement-id as-of-date f]
  (loop [cursor nil
         n 0]
    (let [page (cash-accounts/get-accounts
                config
                organization-id
                (when cursor {:after cursor}))]
      (if (error/anomaly? page)
        page
        (let [processed
              (reduce
               (fn [n account]
                 (let [result (f config
                                 settlement-id
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
  (let [{:keys [organization-id as-of-date]} data]
    (if-let [settlement (get-settlement-account config organization-id)]
      (let [processed (process-customer-accounts config
                                                 organization-id
                                                 (:account-id settlement)
                                                 as-of-date
                                                 accrue-account)]
        (if (error/anomaly? processed)
          processed
          {:organization-id organization-id
           :as-of-date as-of-date
           :accounts-processed processed}))
      (error/reject :interest/no-settlement
                    "No settlement account found"))))

(defn capitalize-monthly
  [config data]
  (let [{:keys [organization-id as-of-date]} data]
    (if-let [settlement (get-settlement-account config organization-id)]
      (let [processed (process-customer-accounts config
                                                 organization-id
                                                 (:account-id settlement)
                                                 as-of-date
                                                 capitalize-account)]
        (if (error/anomaly? processed)
          processed
          {:organization-id organization-id
           :as-of-date as-of-date
           :accounts-processed processed}))
      (error/reject :interest/no-settlement
                    "No settlement account found"))))
