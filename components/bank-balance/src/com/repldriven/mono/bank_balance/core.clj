(ns com.repldriven.mono.bank-balance.core
  (:require
    [com.repldriven.mono.bank-balance.domain :as domain]
    [com.repldriven.mono.bank-balance.store :as store]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- get-policies
  [txn account-id opts]
  (or (:policies opts)
      (policy/get-effective-policies txn {:account-id account-id})))

(defn new-balance
  "Creates a new balance. opts supports `:policies` to override
  policy resolution."
  ([txn data]
   (new-balance txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [account-id balance-type currency balance-status]} data]
        (let-nom>
          [policies (get-policies txn account-id opts)
           existing (store/find-balance txn
                                        account-id
                                        balance-type
                                        currency
                                        balance-status)
           balance (domain/new-balance data (some? existing) policies)
           _ (store/save-balance txn balance)]
          balance))))))

(defn new-balances
  "Creates multiple balances in a single transaction.
  Short-circuits on the first anomaly. opts supports
  `:policies` to override policy resolution."
  ([txn data]
   (new-balances txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn (:account-id (first data)) opts)]
        (reduce (fn [acc item]
                  (let [result (new-balance txn item {:policies policies})]
                    (if (error/anomaly? result)
                      (reduced result)
                      (conj acc result))))
                []
                data))))))

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

(defn- load-account-balances
  "Returns a map of account-id → vector of balances for the
  distinct account-ids referenced by legs."
  [txn legs]
  (reduce (fn [acc account-id]
            (let [result (store/get-balances txn account-id)]
              (if (error/anomaly? result)
                (reduced result)
                (assoc acc account-id result))))
          {}
          (distinct (map :account-id legs))))

(defn apply-legs
  "Applies all legs to balances within a transaction.
  `transaction-type` is required and threaded into the
  computed `:available` limit check. opts supports
  `:policies` to override policy resolution."
  ([txn legs transaction-type]
   (apply-legs txn legs transaction-type {}))
  ([txn legs transaction-type opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn (:account-id (first legs)) opts)
         account-balances (load-account-balances txn legs)
         changed (domain/apply-legs account-balances
                                    legs
                                    transaction-type
                                    policies)]
        (reduce (fn [_ balance]
                  (let [result (store/save-balance txn balance)]
                    (when (error/anomaly? result)
                      (reduced result))))
                nil
                changed))))))
