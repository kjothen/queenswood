(ns com.repldriven.queenswood.clearbank-relay.relay
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.event.interface :as event]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :refer [assoc-some]]))

(defn ->handler
  "Build the changelog relay handler. For each outbox entry it publishes
  the stored event to the bus verbatim (the payload is already
  Avro-serialised at webhook time). On publish failure it throws, so the
  changelog checkpoint does not advance — the entry is redriven on the
  next poll and downstream dedupes. That is the at-least-once relay."
  [{:keys [bus event-channel]}]
  (fn [_ctx changelog-bytes]
    (let [{:keys [event-name payload correlation-id causation-id traceparent]}
          (schema/pb->ClearbankOutboxEvent changelog-bytes)
          envelope (-> (event/envelope event-name causation-id correlation-id)
                       (assoc :payload payload)
                       ;; The span that wrote the outbox entry, so the
                       ;; consumer joins that trace rather than opening
                       ;; its own.
                       (assoc-some :traceparent traceparent))
          res (event/publish bus envelope {:event-channel event-channel})]
      (when (error/anomaly? res)
        (log/error "ClearBank relay publish failed; will redrive" res)
        ;; nosemgrep: no-raw-throw
        (throw (ex-info "ClearBank relay publish failed" {:anomaly res}))))))
