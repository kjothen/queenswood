(ns com.repldriven.queenswood.person-identification.store
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.queenswood.fdb.interface :as fdb]))

(def ^:private store-name "person-identifications")

(def transact fdb/transact)

(defn save-person-identification
  [txn person-identification]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn store-name)
      (schema/PersonIdentification->java person-identification)))
   :person-identification/save
   "Failed to save person identification"))

(defn get-person-identification
  [txn party-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name) party-id)
             schema/pb->PersonIdentification))
   :person-identification/get
   "Failed to load person identification"))
