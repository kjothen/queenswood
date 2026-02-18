(ns com.repldriven.mono.command.response
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.data.json :as json]))

(defn- req->ids
  [req]
  (let [idempotency-key (get-in req [:headers "idempotency-key"])
        correlation-id (or (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    [idempotency-key correlation-id]))

(defn- ->command-error
  [idempotency-key correlation-id category details]
  {"id" (str (utility/uuidv7))
   "correlation_id" correlation-id
   "causation_id" idempotency-key
   "traceparent" (telemetry/inject-traceparent)
   "tracestate" nil
   "status" "error"
   "data" nil
   "error" (json/write-str {:category category :details details})})

(defn command-response
  "Build a structured command response from a command and its result.

  On success: status ok, data JSON-encoded result, error nil.
  On anomaly: builds an error response."
  [{:strs [id correlation_id]} result]
  (if (error/anomaly? result)
    (->command-error id
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

(defn req->command-response
  "Build a command-response from an HTTP request and a result.

  If result is an anomaly, builds an error response.
  Otherwise (not yet used), builds a success response."
  [req result]
  (let [[idempotency-key correlation-id] (req->ids req)]
    (if (error/anomaly? result)
      (->command-error idempotency-key
                       correlation-id
                       (error/kind result)
                       (dissoc result :category))
      result)))
