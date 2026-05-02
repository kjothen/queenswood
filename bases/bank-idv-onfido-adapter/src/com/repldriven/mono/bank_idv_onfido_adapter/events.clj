(ns com.repldriven.mono.bank-idv-onfido-adapter.events
  "Avro-serializes a payload and publishes it onto the bus as a
  named event. Mirrors `bank-clearbank-adapter.events`."
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
  ([bus schemas event-name causation-id correlation-id data]
   (publish bus schemas event-name causation-id correlation-id data {}))
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
