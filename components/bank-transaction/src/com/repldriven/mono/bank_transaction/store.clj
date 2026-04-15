(ns com.repldriven.mono.bank-transaction.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn get-transactions
  "Returns transaction legs for an account, enriched with
  the parent transaction's type, status, and reference.
  Queries the TransactionLeg_by_account index."
  [txn account-id]
  (fdb/transact
   txn
   (fn [txn]
     (let [leg-store (fdb/open txn "transaction-legs")
           txn-store (fdb/open txn "transactions")
           legs (mapv schema/pb->TransactionLeg
                      (fdb/query-records leg-store
                                         "TransactionLeg"
                                         "account_id"
                                         account-id))]
       (mapv (fn [leg]
               (let [txn-record (fdb/load-record txn-store
                                                 (:transaction-id leg))
                     parent (when txn-record
                              (schema/pb->Transaction txn-record))]
                 (merge leg
                        (select-keys parent
                                     [:transaction-type
                                      :status
                                      :reference]))))
             legs)))
   :transaction/list
   "Failed to list account transactions"))
