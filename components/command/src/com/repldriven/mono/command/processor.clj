(ns com.repldriven.mono.command.processor
  (:require
    [com.repldriven.mono.command.response :as response]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.mqtt.interface :as mqtt]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [clojure.core.async :as async]
    [clojure.data.json :as json])
  (:import
    (org.apache.pulsar.client.api Consumer Message)))

(defn process
  "Process commands from Pulsar, send replies via MQTT.

  Args:
  - consumer: Pulsar consumer instance
  - mqtt-client: MQTT client instance
  - schema: Pulsar Avro schema for command messages
  - process-fn: Function that takes command data and returns response or anomaly
  - opts: Optional map with keys:
    - :timeout-ms - Timeout in milliseconds (default 10000)

  Returns: {:stop channel}
  - Send to :stop channel to stop processing"
  ([consumer mqtt-client schema process-fn]
   (process consumer mqtt-client schema process-fn {}))
  ([^Consumer consumer mqtt-client schema process-fn opts]
   (let [{:keys [timeout-ms] :or {timeout-ms 10000}} opts
         {:keys [c stop]} (pulsar/receive consumer schema timeout-ms)]
     (async/thread
      (loop []
        (when-let [{:keys [^Message message data]} (async/<!! c)]
          (let [{:strs [id reply_to]} data
                resp (response/command-response data (process-fn data))]
            (log/debugf
             "command.processor/process: [data=%s, response=%s, reply-topic=%s]"
             data
             resp
             reply_to)
            (error/nom-do> [(mqtt/publish mqtt-client
                                          reply_to
                                          (json/write-str resp))
                            (pulsar/acknowledge consumer message)]
             (log/anomaly {:message "Error processing command" :id id})))
          (recur))))
     {:stop stop})))
