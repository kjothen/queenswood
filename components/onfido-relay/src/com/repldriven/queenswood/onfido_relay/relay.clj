(ns com.repldriven.queenswood.onfido-relay.relay
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.event.interface :as event]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :refer [assoc-some]]))

(defn ->handler
  "Build the changelog relay handler. Publishes each stored outbox event
  to the bus verbatim (the payload is Avro-serialised at webhook time).
  On publish failure it throws, so the checkpoint does not advance and
  the entry is redriven — at-least-once; downstream dedupes."
  [{:keys [bus event-channel]}]
  (fn [_ctx changelog-bytes]
    (let [{:keys [event-name payload correlation-id causation-id traceparent]}
          (schema/pb->OnfidoOutboxEvent changelog-bytes)
          envelope (-> (event/envelope event-name causation-id correlation-id)
                       (assoc :payload payload)
                       ;; The span that wrote the outbox entry, so the
                       ;; consumer joins that trace rather than opening
                       ;; its own.
                       (assoc-some :traceparent traceparent))
          res (event/publish bus envelope {:event-channel event-channel})]
      (when (error/anomaly? res)
        (log/error "Onfido relay publish failed; will redrive" res)
        ;; nosemgrep: no-raw-throw
        (throw (ex-info "Onfido relay publish failed" {:anomaly res}))))))
