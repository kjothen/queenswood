(ns com.repldriven.mono.bank-idempotency.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "idempotency")

(defn save
  "Persist an idempotency cache entry. `entry` is the map shape of
  the `Idempotency` proto: `:principal-id :operation :idempotency-key
  :state :status :body :created-at :expires-at`. State is `\"pending\"`
  for in-flight markers and `\"completed\"` for finished responses."
  [txn entry]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/Idempotency->java entry)))
   :idempotency/save
   "Failed to save idempotency entry"))

(defn lookup
  "Load the cached entry for [principal-id operation idempotency-key],
  or nil if no entry exists. Returns an anomaly on FDB error."
  [txn principal-id operation idempotency-key]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name)
                              principal-id
                              operation
                              idempotency-key)
             schema/pb->Idempotency))
   :idempotency/lookup
   "Failed to load idempotency entry"))

(defn delete
  "Remove the entry for [principal-id operation idempotency-key]. Used
  to release a `pending` claim on non-cacheable (5xx) responses so the
  caller can retry immediately."
  [txn principal-id operation idempotency-key]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/delete-record (fdb/open txn store-name)
                        principal-id
                        operation
                        idempotency-key))
   :idempotency/delete
   "Failed to delete idempotency entry"))
