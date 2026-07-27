(ns com.repldriven.queenswood.changelog-relay.consumer
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn ->handler
  [{:keys [event-channel processor]}]
  (fn [data]
    (let [parent-ctx (telemetry/extract-parent-context data)]
      (telemetry/with-span-parent
       "process-event"
       parent-ctx
       (select-keys data [:id :event :correlation-id :causation-id])
       (fn []
         (let [result (processor/process processor data)]
           (when (error/anomaly? result)
             (log/error "Event processing failed; asking for redelivery"
                        {:event-channel event-channel :anomaly result})
             ;; The consumer loop catches this and negative-acks, so the
             ;; broker redelivers and eventually dead-letters. Returning
             ;; the anomaly instead would ack it and lose the event.
             ;; nosemgrep: no-raw-throw
             (throw (ex-info "Event processing failed" {:anomaly result})))
           result))))))

(defn start
  [{:keys [bus event-channel] :as config}]
  (message-bus/subscribe bus event-channel (->handler config))
  {:stop (fn [] (message-bus/unsubscribe bus event-channel))})
