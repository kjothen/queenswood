(ns com.repldriven.mono.command.request
  (:require
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [clojure.data.json :as json]))

(defn- req->ids
  [req]
  (let [idempotency-key (get-in req [:headers "idempotency-key"])
        correlation-id (or (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    [idempotency-key correlation-id]))

(defn req->command-request
  "Build a command wire message from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and correlation-id from headers)
  - command: command name string
  - data: optional data map (JSON-encoded if present)

  Returns a command map ready for Pulsar, with reply_to set to
  mqtt://replies/<idempotency-key>."
  [req command data]
  (let [[idempotency-key correlation-id] (req->ids req)]
    {"command" command
     "id" idempotency-key
     "correlation_id" correlation-id
     "causation_id" nil
     "traceparent" (telemetry/inject-traceparent)
     "tracestate" nil
     "data" (when data (json/write-str data))
     "reply_to" (str "mqtt://replies/" idempotency-key)}))
