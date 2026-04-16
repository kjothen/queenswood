(ns com.repldriven.mono.bank-transaction.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "transactions")
(def ^:private legs-store-name "transaction-legs")

(def transact fdb/transact)

(defn save-transaction
  "Saves a transaction record. Returns nil or anomaly."
  [txn transaction]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/Transaction->java transaction)))
   :transaction/save
   "Failed to save transaction"))

(defn save-legs
  "Saves transaction legs. Short-circuits on the first
  anomaly. Returns nil or anomaly."
  [txn legs]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn legs-store-name)]
       (reduce (fn [_ leg]
                 (let [result (fdb/save-record
                               store
                               (schema/TransactionLeg->java leg))]
                   (when (error/anomaly? result)
                     (reduced result))))
               nil
               legs)))
   :transaction/save-legs
   "Failed to save transaction legs"))

(defn get-transactions
  "Returns transaction legs for an account, enriched with
  the parent transaction's type, status, and reference.
  Queries the TransactionLeg_by_account index."
  [txn account-id]
  (fdb/transact
   txn
   (fn [txn]
     (let [leg-store (fdb/open txn legs-store-name)
           txn-store (fdb/open txn store-name)
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
