(ns com.repldriven.mono.command.processor
  (:require
    [com.repldriven.mono.command.response :as response]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn process
  "Process command envelopes via message-bus.

  Extracts the parent trace context from each incoming
  envelope and creates a span that covers both the
  process-fn call and the response send. This ensures
  inject-traceparent in the response has an active span.

  The process-fn receives the raw command envelope
  (including payload bytes) and is responsible for
  deserializing the payload.

  Args:
  - bus: message-bus instance
  - process-fn: function that takes a command envelope
    and returns a result map or anomaly. On success the
    result should include a \"record_id\" key.
  - opts: optional map (reserved for future use)

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus process-fn] (process bus process-fn {}))
  ([bus process-fn _opts]
   (message-bus/subscribe
    bus
    :command
    (fn [data]
      (let [parent-ctx (telemetry/extract-parent-context data)]
        (telemetry/with-span-parent
         "process-command"
         parent-ctx
         (select-keys data [:id :command :correlation-id :causation-id])
         (fn []
           (let [resp (response/command-response data (process-fn data))]
             (log/debugf "command.processor/process: [data=%s, response=%s]"
                         data
                         resp)
             (message-bus/send bus :command-response resp)))))))
   {:stop (fn [] (message-bus/unsubscribe bus :command))}))
