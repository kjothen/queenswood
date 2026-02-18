(ns com.repldriven.mono.command.interface
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.command.core :as core]))

(defn req->command-request
  "Build a command wire message from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and correlation-id from headers)
  - command: command name string
  - data: optional data map (JSON-encoded if present)

  Returns a command map ready for Pulsar, with reply_to set to
  mqtt://replies/<idempotency-key>."
  [req command data]
  (core/req->command-request req command data))

(defn ->command-error
  "Build a command-response-shaped error body.

  Args:
  - idempotency-key: the originating command id, used as causation-id
  - correlation-id: the correlation chain id
  - category: error category keyword
  - details: error details map

  Returns a command-response map with status \"error\"."
  [idempotency-key correlation-id category details]
  (core/->command-error idempotency-key correlation-id category details))

(defn req->command-response
  "Build a command-response from an HTTP request.

  Two arities:
  - [req result] - builds from an anomaly result
  - [req category details] - builds an explicit error response"
  ([req result] (core/req->command-response req result))
  ([req category details] (core/req->command-response req category details)))

(def specs
  "Command Malli specs for request/response validation.

  Includes:
  - :command - Command data structure
  - :command-request - HTTP request wrapper
  - :command-result - Command processing result
  - :command-response - HTTP response wrapper"
  core/specs)

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
   (core/process consumer mqtt-client schema process-fn))
  ([consumer mqtt-client schema process-fn opts]
   (core/process consumer mqtt-client schema process-fn opts)))

(defn send
  "Send a command via Pulsar and wait for reply via MQTT.

  Args:
  - producer: Pulsar producer instance
  - mqtt-client: MQTT client instance
  - command: Command data map
  - opts: Optional map with keys:
    - :timeout-ms - Timeout in milliseconds (default 10000)

  Returns: Response map or anomaly"
  ([producer mqtt-client command] (core/send producer mqtt-client command))
  ([producer mqtt-client command opts]
   (core/send producer mqtt-client command opts)))
