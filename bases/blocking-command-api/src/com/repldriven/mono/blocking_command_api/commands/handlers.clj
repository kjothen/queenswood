(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]))

(defn create
  [request]
  (let [{:keys [parameters]} request
        bus (:message-bus request)
        schemas (:avro request)
        {:keys [body]} parameters
        {:strs [command data]} body
        schema (get schemas command)]
    (if-not schema
      {:status 400
       :body (command/req->command-response
              request
              (error/fail :command/unknown-schema
                          {:message "No Avro schema for command"
                           :command command}))}
      (let [payload (avro/serialize schema data)]
        (if (error/anomaly? payload)
          {:status 500 :body (command/req->command-response request payload)}
          (let [cmd (command/req->command-request request command payload)
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
                                          {:message
                                           "Command reply timed out"}))}
                      {:status 200 :body result})))))))))))
