(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
   [com.repldriven.mono.mqtt.interface :as mqtt]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.telemetry.interface :as telemetry]

   [clojure.data.json :as json]))

(defn create
  [request]
  (let [{:keys [parameters mqtt-client pulsar-producers telemetry/idempotency-key telemetry/correlation-id]} request
        {:keys [body]} parameters
        {:keys [data command]} (:data body)
        reply-topic (str "replies/" correlation-id)
        response-promise (promise)
        command {:id idempotency-key
                 :command command
                 :correlation_id correlation-id
                 :causation_id nil
                 :traceparent (telemetry/inject-traceparent)
                 :tracestate nil
                 :data (when data (json/write-str data))}
        producer (get-in pulsar-producers [:command])]

    ;; Subscribe to MQTT reply topic
    (mqtt/subscribe mqtt-client
                    {reply-topic 2}
                    (fn [_topic _msg ^bytes payload]
                      (let [parsed (json/read-str (String. payload "UTF-8") :key-fn keyword)]
                        (deliver response-promise parsed))))

    ;; Publish command to Pulsar (schema-based serialization)
    (pulsar/send producer command)

    ;; Block waiting for response (5 second timeout)
    (let [result (deref response-promise 5000 ::timeout)]
      (if (= result ::timeout)
        {:status 408 :body {:error "Request timeout"}}
        {:status 200 :body {:data result}}))))
