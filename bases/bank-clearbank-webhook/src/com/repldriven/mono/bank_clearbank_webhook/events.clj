(ns com.repldriven.mono.bank-clearbank-webhook.events
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.event.interface :as event]))

(defn- get-schema
  [schemas event-name]
  (or (get schemas event-name)
      (error/fail :webhook/unknown-event
                  {:message "Unknown event schema"
                   :event event-name})))

(defn publish
  "Publish an event with Avro-serialized payload.

  Args:
  - bus: message-bus instance
  - schemas: avro schema map
  - event-name: event name string (also used as schema key)
  - causation-id: ID of the causing action
  - correlation-id: correlation ID for tracing
  - data: map to serialize as event payload"
  ([bus schemas event-name causation-id correlation-id data]
   (publish bus
            schemas
            event-name
            causation-id
            correlation-id
            data
            {}))
  ([bus schemas event-name causation-id correlation-id data opts]
   (let-nom>
     [schema (get-schema schemas event-name)
      payload (avro/serialize schema data)]
     (event/publish bus
                    (assoc (event/envelope event-name
                                           causation-id
                                           correlation-id)
                           :payload
                           payload)
                    opts))))
