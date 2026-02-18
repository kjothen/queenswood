(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
    [com.repldriven.mono.blocking-command-api.errors :as errors]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.mqtt.interface :as mqtt]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.telemetry.interface :as telemetry]
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
  (let [{:keys [parameters mqtt-client pulsar-producers
                telemetry/idempotency-key telemetry/correlation-id]}
        request
        {:keys [body]} parameters
        {:strs [command data]} body
        reply-topic (str "replies/" idempotency-key)
        reply-to (str "mqtt://" reply-topic)
        p (promise)
        command {"command" command
                 "id" idempotency-key
                 "correlation_id" correlation-id
                 "causation_id" nil
                 "traceparent" (telemetry/inject-traceparent)
                 "tracestate" nil
                 "data" (when data (json/write-str data))
                 "reply_to" reply-to}
        producer (get-in pulsar-producers [:command])
        sub (mqtt/subscribe mqtt-client
                            {reply-topic 0}
                            (partial process-mqqt-payload p))]
    (if (error/anomaly? sub)
      {:status 500
       :body (errors/request->command-error-response request
                                                     :command/mqtt-subscribe
                                                     sub)}
      (let [pub (pulsar/send producer command)]
        (if (error/anomaly? pub)
          (do (mqtt/unsubscribe mqtt-client [reply-topic])
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
