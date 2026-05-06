(ns com.repldriven.mono.bank-test-projections.transactions
  (:require
    [com.repldriven.mono.bank-transaction.interface :as transactions]))

(defn project-transactions
  [bank id-mapping]
  (->> id-mapping
       (map (fn [[real-id model-id]]
              [model-id (count (transactions/get-transactions bank real-id))]))
       (into {})))

(defn project-model-transactions
  [model-state]
  (->> (:accounts model-state)
       (map (fn [[acct-id acct]]
              [acct-id (or (:transaction-legs acct) 0)]))
       (into {})))
