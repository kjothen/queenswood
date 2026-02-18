(ns com.repldriven.mono.command.interface
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.command.core :as core]
    [com.repldriven.mono.command.request :as request]
    [com.repldriven.mono.command.response :as response]))

(defn req->command-request
  "Build a command wire message from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and correlation-id from headers)
  - command: command name string
  - data: optional data map (JSON-encoded if present)

  Returns a command map ready for Pulsar, with reply_to set to
  mqtt://replies/<idempotency-key>."
  [req command data]
  (request/req->command-request req command data))

(defn req->command-response
  "Build a command-response from an HTTP request and a result.

  If result is an anomaly, builds an error response.
  Otherwise (not yet used), builds a success response."
  [req result]
  (response/req->command-response req result))

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

  Returns: {:stop channel}
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
