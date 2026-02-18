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
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility])
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

(defn command-request
  "Build a command wire message from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and correlation-id from headers)
  - command: command name string
  - data: optional data map (JSON-encoded if present)

  Returns a command map ready for Pulsar, with reply_to set to
  mqtt://replies/<idempotency-key>."
  [req command data]
  (let [idempotency-key (get-in req [:headers "idempotency-key"])
        correlation-id (or (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    {"command" command
     "id" idempotency-key
     "correlation_id" correlation-id
     "causation_id" nil
     "traceparent" (telemetry/inject-traceparent)
     "tracestate" nil
     "data" (when data (json/write-str data))
     "reply_to" (str "mqtt://replies/" idempotency-key)}))

(defn command-error-response
  "Build a command-response-shaped error body.

  Args:
  - idempotency-key: the originating command id, used as causation-id
  - correlation-id: the correlation chain id
  - category: error category keyword
  - details: error details map

  Returns a command-response map with status \"error\"."
  [idempotency-key correlation-id category details]
  {"id" (str (utility/uuidv7))
   "correlation_id" correlation-id
   "causation_id" idempotency-key
   "traceparent" (telemetry/inject-traceparent)
   "tracestate" nil
   "status" "error"
   "data" nil
   "error" (json/write-str {:category category :details details})})

(defn- command-response
  "Build a structured command response from a command and its result.

  On success: status ok, data JSON-encoded result, error nil.
  On anomaly: delegates to command-error-response."
  [{:strs [id correlation_id]} result]
  (if (error/anomaly? result)
    (command-error-response id
                            correlation_id
                            (error/kind result)
                            (dissoc result :category))
    {"id" (str (utility/uuidv7))
     "correlation_id" correlation_id
     "causation_id" id
     "traceparent" (telemetry/inject-traceparent)
     "tracestate" nil
     "status" "ok"
     "data" (json/write-str result)
     "error" nil}))

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
                (mqtt/unsubscribe mqtt-client [reply-topic])
                (if (= ch timeout-chan)
                  (error/fail :command/timeout
                              {:message "Command reply timed out"})
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
          (let [{:strs [id reply_to]} data
                response (command-response data (process-fn data))]
            (log/debugf
             "command.core/process: [data=%s, response=%s, reply-topic=%s]"
             data
             response
             reply_to)
            (error/with-anomaly? [(mqtt/publish mqtt-client
                                                reply_to
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
         correlation-id (str (utility/uuidv7))
         reply-to (str "mqtt://replies/" correlation-id)
         command-with-correlation
         (assoc command :correlation_id correlation-id :reply_to reply-to)
         send-result (pulsar/send producer command-with-correlation)]
     (if (error/anomaly? send-result)
       send-result
       (async/<!! (await-reply mqtt-client reply-to timeout-ms))))))
