(ns com.repldriven.mono.command.core
  (:refer-clojure :exclude [send])
  (:require
    [clojure.core.async :as async]
    [clojure.data.json :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.mqtt.interface :as mqtt]
    [com.repldriven.mono.pulsar.interface :as pulsar])
  (:import
    (org.apache.pulsar.client.api Consumer Message)))

(def specs
  "Command Malli specs for request/response validation.

  Loaded from schemas/command/command.edn resource file. Includes:
  - :command - Command data structure
  - :command-request - HTTP request wrapper
  - :command-result - Command processing result
  - :command-response - HTTP response wrapper"
  (delay (-> "schemas/command/command.edn"
             io/resource
             slurp
             edn/read-string)))

(defn- await-reply
  "Wait for a reply on MQTT topic with timeout."
  [mqtt-client reply-topic timeout-ms]
  (let [result-chan (async/chan 1)
        timeout-chan (async/timeout timeout-ms)
        callback (fn [_topic ^bytes payload]
                   (try (let [response (json/read-str (String. payload "UTF-8")
                                                      :key-fn
                                                      keyword)]
                          (async/put! result-chan response))
                        (catch Exception e
                          (async/put! result-chan
                                      (error/fail :command/parse
                                                  (str "Failed to parse reply: "
                                                       (.getMessage e)))))))]
    (mqtt/subscribe mqtt-client reply-topic callback)
    (async/go (let [[v ch] (async/alts! [result-chan timeout-chan])]
                (mqtt/unsubscribe mqtt-client reply-topic)
                (if (= ch timeout-chan)
                  (error/fail :command/timeout "Command reply timed out")
                  v)))))

(defn process
  "Process commands from Pulsar, send replies via MQTT.

  Args:
  - consumer: Pulsar consumer instance
  - mqtt-client: MQTT client instance
  - schema: Pulsar Avro schema for command messages
  - process-fn: Function that takes command data and returns response or anomaly
  - opts: Optional map with keys:
    - :timeout-ms - Timeout in milliseconds (default 10000)

  Returns: {:c channel :stop channel}
  - Messages arrive on :c channel as processed results
  - Send to :stop channel to stop processing"
  ([consumer mqtt-client schema process-fn]
   (process consumer mqtt-client schema process-fn {}))
  ([^Consumer consumer mqtt-client schema process-fn opts]
   (let [{:keys [timeout-ms] :or {timeout-ms 10000}} opts
         {:keys [c stop]} (pulsar/receive consumer schema timeout-ms)
         result-chan (async/chan 1)]
     (async/thread
      (loop []
        (when-let [{:keys [^Message message data]} (async/<!! c)]
          (let [id (:id data)
                reply-topic (some-> (:reply_to data)
                                    (.replace "mqtt://" ""))
                response (process-fn data)]
            (log/debugf
             "command.core/process: [data=%s, response=%s, reply-topic=%s]"
             data
             response
             reply-topic)
            (error/with-anomaly? [(mqtt/publish mqtt-client
                                                reply-topic
                                                (json/write-str response))
                                  (pulsar/acknowledge consumer message)]
             (log/anomaly {:message "Error processing command" :id id})))
          (recur))))
     {:c result-chan :stop stop})))

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
         correlation-id (str (java.util.UUID/randomUUID))
         reply-topic (str "replies/" correlation-id)
         reply-to (str "mqtt://" reply-topic)
         command-with-correlation
         (assoc command :correlation_id correlation-id :reply_to reply-to)
         send-result (pulsar/send producer command-with-correlation)]
     (if (error/anomaly? send-result)
       send-result
       (async/<!! (await-reply mqtt-client reply-topic timeout-ms))))))
