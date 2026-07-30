(ns com.repldriven.queenswood.onfido-relay.store
  (:require
    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :refer [assoc-some]]))

(def ^:private store-name "onfido-outbox")

(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-event
  "Persist an outbox event and append it to the store's changelog in a
  single transaction. A duplicate `dedup-key` fails the unique index."
  [txn event]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)
           ;; Captured here rather than by the caller because this runs on
           ;; the thread that holds the span, and every writer goes through
           ;; it. Absent when nothing is being traced — an optional proto
           ;; scalar wants the key gone, not nil.
           event (assoc-some event :traceparent (telemetry/inject-traceparent))]
       (let-nom>
         [_ (fdb/save-record store (schema/OnfidoOutboxEvent->java event))
          _ (fdb/write-changelog txn
                                 store-name
                                 (:outbox-id event)
                                 (schema/OnfidoOutboxEvent->pb event))]
         event)))
   :onfido-outbox/save
   "Failed to save onfido outbox event"))
