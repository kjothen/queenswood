(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
    [com.repldriven.mono.blocking-command-api.errors :as errors]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.mqtt.interface :as mqtt]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.error.interface :as error]))

(defn- process-mqqt-payload
  [p _ _topic ^bytes payload]
  (let [message (String. payload "UTF-8")]
    (log/debugf
     "blocking-command-api.commands.handlers.process-mqqt-payload [_topic=%s, payload=%s]"
     _topic
     message)
    (let [parsed (json/read-str message)] (deliver p parsed))))

(defn create
  [request]
  (let [{:keys [parameters mqtt-client pulsar-producers]} request
        {:keys [body]} parameters
        {:strs [command data]} body
        cmd (command/req->command request command data)
        reply-to (get cmd "reply_to")
        p (promise)
        producer (get-in pulsar-producers [:command])
        sub (mqtt/subscribe mqtt-client
                            {reply-to 0}
                            (partial process-mqqt-payload p))]
    (if (error/anomaly? sub)
      {:status 500
       :body (errors/request->command-error-response request
                                                     :command/mqtt-subscribe
                                                     sub)}
      (let [pub (pulsar/send producer cmd)]
        (if (error/anomaly? pub)
          (do (mqtt/unsubscribe mqtt-client [reply-to])
              {:status 500
               :body (errors/request->command-error-response
                      request
                      :command/pulsar-send
                      pub)})
          (let [result (deref p 5000 ::timeout)]
            (if (= result ::timeout)
              {:status 408
               :body (errors/request->command-error-response
                      request
                      :command/timeout
                      {:message "Command reply timed out"})}
              {:status 200 :body result})))))))
