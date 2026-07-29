(ns com.repldriven.queenswood.clearbank-relay.store
  (:require
    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(def ^:private store-name "clearbank-outbox")

(def transact fdb/transact)

(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-event
  "Persist an outbox event and append it to the store's changelog in a
  single transaction — the transactional-outbox write. A duplicate
  `dedup-key` fails the unique index."
  [txn event]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (let-nom>
         [_ (fdb/save-record store (schema/ClearbankOutboxEvent->java event))
          _ (fdb/write-changelog store
                                 store-name
                                 (:outbox-id event)
                                 (schema/ClearbankOutboxEvent->pb event))]
         event)))
   :clearbank-outbox/save
   "Failed to save clearbank outbox event"))
