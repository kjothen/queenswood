(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:refer-clojure :exclude [type])
  (:require
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.mqtt.interface :as mqtt]
   [com.repldriven.mono.pulsar.interface :as pulsar]

   [clojure.data.json :as json])
  (:import
   (java.util UUID)))

(defn create
  [request]
  (let [{:keys [parameters mqtt-client pulsar-producers]} request
        {:keys [body]} parameters
        {:keys [data]} body
        {:keys [correlation_id type id]} data
        reply-topic (str "replies/" correlation_id)
        response-promise (promise)
        command {:correlation_id correlation_id
                 :type type
                 :id id}
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
