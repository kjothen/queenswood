(ns com.repldriven.mono.bank-test-projections.balances
  (:require
    [com.repldriven.mono.bank-balance-query.interface :as balance]))

(defn- available
  [bank account-id]
  (or (-> (balance/get-balances bank account-id)
          :available-balance
          :value)
      0))

(defn project-balances
  [bank id-mapping]
  (->> id-mapping
       (map (fn [[real-id model-id]]
              [model-id (available bank real-id)]))
       (into {})))

(defn project-model-balances
  [model-state]
  (-> (:accounts model-state)
      (update-vals :available)))
