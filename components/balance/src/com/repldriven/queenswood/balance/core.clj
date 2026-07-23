(ns com.repldriven.queenswood.balance.core
  (:require
    [com.repldriven.queenswood.balance.domain :as domain]
    [com.repldriven.queenswood.balance.store :as store]

    [com.repldriven.queenswood.balance-query.interface :as q]
    [com.repldriven.queenswood.policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- get-policies
  [txn account-id opts]
  (or (:policies opts)
      (policy/get-effective-policies txn {:account-id account-id})))

(defn new-balance
  ([txn data]
   (new-balance txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [account-id balance-type currency balance-status]} data]
        (let-nom>
          [policies (get-policies txn account-id opts)
           existing (q/find-balance txn
                                    account-id
                                    balance-type
                                    currency
                                    balance-status)
           balance (domain/new-balance data (some? existing) policies)
           _ (store/save-balance txn balance)]
          balance))))))

(defn new-balances
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

(defn set-carry
  [txn account-id balance-type currency balance-status carry]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [balance (q/get-balance txn
                               account-id
                               balance-type
                               currency
                               balance-status)
        updated (assoc balance :credit-carry carry)
        _ (store/save-balance txn updated)]
       updated))))

(defn- load-account-balances
  [txn legs]
  (reduce (fn [acc account-id]
            (let [result (q/list-balances txn account-id)]
              (if (error/anomaly? result)
                (reduced result)
                (assoc acc account-id result))))
          {}
          (distinct (map :account-id legs))))

(defn apply-legs
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
