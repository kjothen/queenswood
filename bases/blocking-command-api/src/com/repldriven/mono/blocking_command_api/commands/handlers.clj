(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.mqtt.interface :as mqtt]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.avro.interface :as avro]

   [clojure.data.json :as json])
  (:import
   (java.util UUID)))

(defn create
  [request]
  (let [{:keys [parameters mqtt-client pulsar-producers pulsar-schemas]} request
        {:keys [body]} parameters
        {:keys [data]} body
        {:keys [type id]} data
        correlation-id (str (UUID/randomUUID))
        reply-topic (str "replies/" correlation-id)
        response-promise (promise)
        command {:correlation_id correlation-id
                 :type type
                 :id id}
        producer (get-in pulsar-producers [:command])
        schema (pulsar/schema->avro (get-in pulsar-schemas [:command :schema]))
        command-bytes (avro/serialize schema command)]

    ;; Subscribe to MQTT reply topic
    (mqtt/subscribe mqtt-client
                    {reply-topic 2}
                    (fn [_topic _msg ^bytes payload]
                      (let [parsed (json/read-str (String. payload "UTF-8") :key-fn keyword)]
                        (deliver response-promise parsed))))

    ;; Publish command to Pulsar (manually serialized with Avro)
    (pulsar/send producer command-bytes)

    ;; Block waiting for response (5 second timeout)
    (let [result (deref response-promise 5000 ::timeout)]
      (if (= result ::timeout)
        {:status 408 :body {:error "Request timeout"}}
        {:status 200 :body {:data result}}))))
