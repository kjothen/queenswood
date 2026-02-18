(ns com.repldriven.mono.command.sender
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.mqtt.interface :as mqtt]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [clojure.core.async :as async]
    [clojure.data.json :as json]))

(defn- await-reply
  "Wait for a reply on MQTT topic with timeout."
  [mqtt-client reply-topic timeout-ms]
  (let [result-chan (async/chan 1)
        timeout-chan (async/timeout timeout-ms)
        callback (fn [_topic ^bytes payload]
                   (async/put! result-chan
                               (error/try-nom :command/parse
                                              "Failed to parse reply"
                                              (json/read-str
                                               (String. payload "UTF-8")))))]
    (mqtt/subscribe mqtt-client {reply-topic 0} callback)
    (async/go (let [[v ch] (async/alts! [result-chan timeout-chan])]
                (error/nom-do> [(mqtt/unsubscribe mqtt-client [reply-topic])]
                 (log/anomaly {:message "Error unsubscribing from reply topic"
                               :topic reply-topic}))
                (if (= ch timeout-chan)
                  (error/fail :command/timeout
                              {:message "Command reply timed out"})
                  v)))))

(defn send
  "Send a command via Pulsar and wait for reply via MQTT.

  Args:
  - producer: Pulsar producer instance
  - mqtt-client: MQTT client instance
  - command: Command data map
  - opts: Optional map with keys:
    - :timeout-ms - Timeout in milliseconds (default 10000)

  Returns: Response map or anomaly"
  ([producer mqtt-client command] (send producer mqtt-client command {}))
  ([producer mqtt-client command opts]
   (let [{:keys [timeout-ms] :or {timeout-ms 10000}} opts
         {:strs [reply_to]} command
         send-result (pulsar/send producer command)]
     (if (error/anomaly? send-result)
       send-result
       (async/<!! (await-reply mqtt-client reply_to timeout-ms))))))
