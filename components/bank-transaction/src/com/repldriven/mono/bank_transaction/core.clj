(ns com.repldriven.mono.bank-transaction.core
  (:require
    [com.repldriven.mono.bank-transaction.domain :as domain]
    [com.repldriven.mono.bank-transaction.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn record
  "Records a transaction and its legs. Does not update
  balances — callers must call apply-legs separately.
  Returns the transaction map (with :legs) or anomaly."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [legs]} data
           transaction (domain/new-transaction data)
           {:keys [transaction-id currency]} transaction
           legs' (mapv (fn [leg]
                         (domain/new-leg leg transaction-id currency))
                       legs)]
       (let-nom>
         [_ (store/save-transaction txn transaction)
          _ (store/save-legs txn legs')]
         (assoc transaction :legs legs'))))))

(defn record-transaction
  "Records a transaction with its legs and applies legs to
  balances in a single atomic transaction. Used by the
  command processor. Returns the transaction map or
  anomaly."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [result (record txn data)
        _ (balances/apply-legs txn
                               (:legs result)
                               (:transaction-type result))]
       result))))
