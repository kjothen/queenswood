(ns com.repldriven.mono.command.sender
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]))

(defn send
  "Send a command via message-bus and wait for reply.

  Args:
  - bus: message-bus instance
  - command: command data map
  - opts: optional map with keys:
    - :timeout-ms - timeout in milliseconds (default 10000)

  Returns: response map or anomaly"
  ([bus command] (send bus command {}))
  ([bus command opts]
   (let [{:keys [timeout-ms] :or {timeout-ms 10000}} opts
         p (promise)
         sub (message-bus/subscribe bus
                                    :command-response
                                    (fn [data] (deliver p data)))]
     (if (error/anomaly? sub)
       sub
       (let [pub (message-bus/send bus :command command)]
         (if (error/anomaly? pub)
           (do (message-bus/unsubscribe bus :command-response) pub)
           (let [result (deref p timeout-ms ::timeout)]
             (message-bus/unsubscribe bus :command-response)
             (if (= result ::timeout)
               (error/fail :command/timeout
                           {:message "Command reply timed out"})
               result))))))))
