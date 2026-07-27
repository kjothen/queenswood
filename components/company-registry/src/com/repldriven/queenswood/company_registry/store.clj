(ns com.repldriven.queenswood.company-registry.store
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]
    [com.repldriven.queenswood.fdb.interface :as fdb]))

(def ^:private store-name "companies")

(defn save-company
  [txn-or-config company]
  (fdb/transact
   txn-or-config
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (fdb/save-record store (schema/Company->java company))
       company))
   :company-registry/save
   "Failed to save company"))

(defn get-company
  [txn-or-config company-number]
  (fdb/transact
   txn-or-config
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name) company-number)
             schema/pb->Company))
   :company-registry/get
   "Failed to load company"))
