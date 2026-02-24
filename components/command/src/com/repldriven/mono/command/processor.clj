(ns com.repldriven.mono.command.processor
  (:require
    [com.repldriven.mono.command.response :as response]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]))

(defn process
  "Process commands via message-bus.

  Args:
  - bus: message-bus instance
  - process-fn: function that takes command data and returns
    result or anomaly
  - opts: optional map (reserved for future use)

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus process-fn] (process bus process-fn {}))
  ([bus process-fn _opts]
   (message-bus/subscribe
    bus
    :command
    (fn [data]
      (let [resp (response/command-response data (process-fn data))]
        (log/debugf "command.processor/process: [data=%s, response=%s]"
                    data
                    resp)
        (message-bus/send bus :command-response resp))))
   {:stop (fn [] (message-bus/unsubscribe bus :command))}))
