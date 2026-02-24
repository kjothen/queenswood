(ns com.repldriven.mono.command.interface
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.command.processor :as processor]
    [com.repldriven.mono.command.request :as request]
    [com.repldriven.mono.command.response :as response]
    [com.repldriven.mono.command.sender :as sender]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn req->command-request
  "Build a command wire message from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and
    correlation-id from headers)
  - command: command name string
  - data: optional data map (JSON-encoded if present)

  Returns a command map ready for message-bus."
  [req command data]
  (request/req->command-request req command data))

(defn req->command-response
  "Build a command-response from an HTTP request and a result.

  If result is an anomaly, builds an error response.
  Otherwise (not yet used), builds a success response."
  [req result]
  (response/req->command-response req result))

(defn command-response
  "Build a structured command response from a command and
  its result.

  On success: status ok, data JSON-encoded result, error nil.
  On anomaly: builds an error response."
  [command result]
  (response/command-response command result))

(def specs
  "Command Malli specs for request/response validation.

  Includes:
  - :command - Command data structure
  - :command-request - HTTP request wrapper
  - :command-result - Command processing result
  - :command-response - HTTP response wrapper"
  (delay (-> "schemas/command/command.edn"
             io/resource
             slurp
             edn/read-string)))

(defn process
  "Process commands via message-bus.

  Args:
  - bus: message-bus instance
  - process-fn: function that takes command data and returns
    result or anomaly
  - opts: optional map (reserved for future use)

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus process-fn] (processor/process bus process-fn))
  ([bus process-fn opts] (processor/process bus process-fn opts)))

(defn send
  "Send a command via message-bus and wait for reply.

  Args:
  - bus: message-bus instance
  - command: command data map
  - opts: optional map with keys:
    - :timeout-ms - timeout in milliseconds (default 10000)

  Returns: response map or anomaly"
  ([bus command] (sender/send bus command))
  ([bus command opts] (sender/send bus command opts)))
