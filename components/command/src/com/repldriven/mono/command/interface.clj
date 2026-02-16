(ns com.repldriven.mono.command.interface
  (:refer-clojure :exclude [send])
  (:require
   [com.repldriven.mono.command.core :as core]))

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
  ([producer mqtt-client command]
   (core/send producer mqtt-client command))
  ([producer mqtt-client command opts]
   (core/send producer mqtt-client command opts)))
