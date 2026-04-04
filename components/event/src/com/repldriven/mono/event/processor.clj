(ns com.repldriven.mono.event.processor
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn process
  "Process event envelopes via message-bus.

  Extracts the parent trace context from each incoming
  envelope and creates a span that covers the handler-fn
  call.

  The handler-fn receives the raw event envelope
  (including payload bytes) and is responsible for
  deserializing the payload.

  Args:
  - bus: message-bus instance
  - handler-fn: function that takes an event envelope
    and processes it
  - opts: optional map with keys:
    - :event-channel - keyword for receiving events

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus handler-fn] (process bus handler-fn {}))
  ([bus handler-fn opts]
   (let [{:keys [event-channel]
          :or {event-channel :event}}
         opts]
     (message-bus/subscribe
      bus
      event-channel
      (fn [data]
        (let [parent-ctx (telemetry/extract-parent-context data)]
          (telemetry/with-span-parent
           "process-event"
           parent-ctx
           (select-keys data
                        [:id :event :correlation-id :causation-id])
           (fn []
             (let [result (error/try-nom
                           :event/process
                           "Event processing failed"
                           (handler-fn data))]
               (log/debugf
                "event.processor/process: [data=%s, result=%s]"
                data
                result)
               (when (error/anomaly? result)
                 (log/errorf
                  "event.processor/process anomaly: %s"
                  result))))))))
     {:stop (fn [] (message-bus/unsubscribe bus event-channel))})))
