(ns com.repldriven.queenswood.onfido-relay.intent
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private store-name "onfido-outbound-intents")

(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-intent
  "Persist a pending outbound intent — the consume-side outbox write. A
  duplicate `dedup-key` (a redelivered submit-idv-check command) fails
  the unique index."
  [txn intent]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/OnfidoOutboundIntent->java intent)))
   :onfido-outbound/save
   "Failed to save onfido outbound intent"))

(defn pending-intents
  "Read every intent still `pending`, via the status index."
  [txn]
  (fdb/transact
   txn
   (fn [txn]
     (mapv schema/pb->OnfidoOutboundIntent
           (fdb/query-records (fdb/open txn store-name)
                              "OnfidoOutboundIntent"
                              "status"
                              "pending"
                              {:index "OnfidoOutboundIntent_by_status"})))
   :onfido-outbound/pending
   "Failed to read pending outbound intents"))

(defn- update-intent
  [txn intent-id f]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (when-let [existing (some-> (fdb/load-record store intent-id)
                                   schema/pb->OnfidoOutboundIntent)]
         (fdb/save-record store
                          (schema/OnfidoOutboundIntent->java (f existing))))))
   :onfido-outbound/update
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
