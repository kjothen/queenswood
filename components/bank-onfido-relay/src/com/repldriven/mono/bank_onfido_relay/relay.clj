(ns com.repldriven.mono.bank-onfido-relay.relay
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.event.interface :as event]
    [com.repldriven.mono.log.interface :as log]))

(defn ->handler
  "Build the changelog relay handler. Publishes each stored outbox event
  to the bus verbatim (the payload is Avro-serialised at webhook time).
  On publish failure it throws, so the checkpoint does not advance and
  the entry is redriven — at-least-once; downstream dedupes."
  [{:keys [bus event-channel]}]
  (fn [_ctx changelog-bytes]
    (let [{:keys [event-name payload correlation-id causation-id]}
          (schema/pb->OnfidoOutboxEvent changelog-bytes)
          envelope (assoc
                    (event/envelope event-name causation-id correlation-id)
                    :payload
                    payload)
          res (event/publish bus envelope {:event-channel event-channel})]
      (when (error/anomaly? res)
        (log/error "Onfido relay publish failed; will redrive" res)
        ;; nosemgrep: no-raw-throw
        (throw (ex-info "Onfido relay publish failed" {:anomaly res}))))))
