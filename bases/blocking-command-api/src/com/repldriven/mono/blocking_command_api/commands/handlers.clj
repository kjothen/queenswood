(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]))

(defn create
  [request]
  (let [{:keys [parameters]} request
        bus (:message-bus request)
        {:keys [body]} parameters
        {:strs [command data]} body
        cmd (command/req->command-request request command data)
        p (promise)
        sub (message-bus/subscribe bus
                                   :command-response
                                   (fn [data] (deliver p data)))]
    (if (error/anomaly? sub)
      {:status 500 :body (command/req->command-response request sub)}
      (let [pub (message-bus/send bus :command cmd)]
        (if (error/anomaly? pub)
          (do (message-bus/unsubscribe bus :command-response)
              {:status 500
               :body (command/req->command-response request pub)})
          (let [result (deref p 5000 ::timeout)]
            (message-bus/unsubscribe bus :command-response)
            (if (= result ::timeout)
              {:status 408
               :body (command/req->command-response
                      request
                      (error/fail :command/timeout
                                  {:message "Command reply timed out"}))}
              {:status 200 :body result})))))))
