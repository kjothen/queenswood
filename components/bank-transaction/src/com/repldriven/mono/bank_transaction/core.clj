(ns com.repldriven.mono.bank-transaction.core
  (:require
    [com.repldriven.mono.bank-transaction.domain :as domain]
    [com.repldriven.mono.bank-transaction.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn record
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
         [_ (domain/validate-legs legs)
          _ (store/save-transaction txn transaction)
          _ (store/save-legs txn legs')]
         (assoc transaction :legs legs'))))))

(defn record-transaction
  [txn data]
  (let [result (store/transact
                txn
                (fn [txn]
                  (let-nom>
                    [result (record txn data)
                     _ (balances/apply-legs txn
                                            (:legs result)
                                            (:transaction-type result))]
                    result)))
        result (if (store/uniqueness-violation? result)
                 (error/reject :transaction/already-recorded
                               "Transaction already recorded")
                 result)]
    result))
