(ns com.repldriven.mono.bank-balance.core
  (:require
    [com.repldriven.mono.bank-balance.domain :as domain]
    [com.repldriven.mono.bank-balance.store :as store]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn new-balance
  "Creates a new balance. Rejects if the balance already
  exists. Returns the balance or anomaly."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [account-id balance-type currency balance-status]} data]
       (let-nom>
         [existing (store/find-balance txn
                                       account-id
                                       balance-type
                                       currency
                                       balance-status)
          balance (domain/new-balance data (some? existing))
          _ (store/save-balance txn balance)]
         balance)))))

(defn new-balances
  "Creates multiple balances in a single transaction.
  Short-circuits on the first anomaly. Returns the
  created balances or anomaly."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (reduce (fn [acc item]
               (let [result (new-balance txn item)]
                 (if (error/anomaly? result)
                   (reduced result)
                   (conj acc result))))
             []
             data))))

(defn get-balances
  "Lists balances for an account, enriched with the
  derived posted-balance and available-balance. Returns
  {:balances [...] :posted-balance {...}
   :available-balance {...}} or anomaly."
  [txn account-id]
  (let-nom>
    [result (store/get-balances txn account-id)]
    (let [currency (:currency (first result) "")
          product-type (:product-type (first result))]
      {:balances result
       :posted-balance (domain/posted-balance result currency)
       :available-balance (domain/available-balance product-type
                                                    result
                                                    currency)})))

(defn- apply-leg
  "Loads the balance for a leg, applies the domain
  transformation, saves. Returns the updated balance or
  anomaly (rejects with :balance/not-found if the
  composite key resolves no balance). "
  [txn {:keys [account-id balance-type currency balance-status] :as leg}]
  (let-nom>
    [balance (store/get-balance txn
                                account-id
                                balance-type
                                currency
                                balance-status)
     balance' (domain/apply-leg balance leg)
     _ (store/save-balance txn balance')]
    balance'))

(defn set-carry
  "Updates the :credit-carry on the balance identified by
  the composite PK. Loads the balance (rejects if missing),
  assocs the new carry, saves. Returns the updated balance
  or anomaly."
  [txn account-id balance-type currency balance-status carry]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [balance (store/get-balance txn
                                   account-id
                                   balance-type
                                   currency
                                   balance-status)
        updated (assoc balance :credit-carry carry)
        _ (store/save-balance txn updated)]
       updated))))

(defn apply-legs
  "Applies all legs to balances within a transaction.
  Returns nil or the first anomaly."
  [txn legs]
  (store/transact
   txn
   (fn [txn]
     (reduce (fn [_ leg]
               (let [result (apply-leg txn leg)]
                 (when (error/anomaly? result)
                   (reduced result))))
             nil
             legs))))
