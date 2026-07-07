(ns com.repldriven.mono.bank-clearbank-relay.intent
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private store-name "clearbank-outbound-intents")

(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-intent
  "Persist a pending outbound intent — the consume-side outbox write. A
  duplicate `dedup-key` (a redelivered submit-payment command) fails the
  unique index."
  [txn intent]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/ClearbankOutboundIntent->java intent)))
   :clearbank-outbound/save
   "Failed to save clearbank outbound intent"))

(defn pending-intents
  "Read every intent still `pending`, via the status index."
  [txn]
  (fdb/transact
   txn
   (fn [txn]
     (mapv schema/pb->ClearbankOutboundIntent
           (fdb/query-records (fdb/open txn store-name)
                              "ClearbankOutboundIntent"
                              "status"
                              "pending"
                              {:index "ClearbankOutboundIntent_by_status"})))
   :clearbank-outbound/pending
   "Failed to read pending outbound intents"))

(defn- update-intent
  [txn intent-id f]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (when-let [existing (some-> (fdb/load-record store intent-id)
                                   schema/pb->ClearbankOutboundIntent)]
         (fdb/save-record store
                          (schema/ClearbankOutboundIntent->java (f
                                                                 existing))))))
   :clearbank-outbound/update
   "Failed to update outbound intent"))

(defn mark-sent
  [txn intent-id]
  (update-intent txn
                 intent-id
                 (fn [i] (assoc i :status "sent" :sent-at (utility/now)))))

(defn mark-attempt
  [txn intent-id attempts]
  (update-intent txn intent-id (fn [i] (assoc i :attempts attempts))))

(defn mark-failed
  [txn intent-id attempts]
  (update-intent txn
                 intent-id
                 (fn [i] (assoc i :status "failed" :attempts attempts))))
