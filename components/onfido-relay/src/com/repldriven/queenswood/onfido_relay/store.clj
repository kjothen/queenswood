(ns com.repldriven.queenswood.onfido-relay.store
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "onfido-outbox")

(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-event
  "Persist an outbox event and append it to the store's changelog in a
  single transaction. A duplicate `dedup-key` fails the unique index."
  [txn event]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (let-nom>
         [_ (fdb/save-record store (schema/OnfidoOutboxEvent->java event))
          _ (fdb/write-changelog store
                                 store-name
                                 (:outbox-id event)
                                 (schema/OnfidoOutboxEvent->pb event))]
         event)))
   :onfido-outbox/save
   "Failed to save onfido outbox event"))
