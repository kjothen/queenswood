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

(defn- serializable-details
  [details]
  (cond (string? details) {:message details}
        (map? details) (into {}
                             (keep (fn [[k v]]
                                     (when-not (instance? Throwable v)
                                       [k (if (keyword? v) (str v) v)])))
                             details)
        :else {:message (str details)}))

(defn- ->command-error
  [causation-id correlation-id category details]
  {"id" (str (utility/uuidv7))
   "correlation_id" correlation-id
   "causation_id" causation-id
   "record_id" ""
   "traceparent" (telemetry/inject-traceparent)
   "tracestate" nil
   "status" "FAILED"
   "payload" nil
   "error" (json/write-str {:category (str category)
                            :details (serializable-details details)})})

(defn command-response
  "Build a structured command response from a command
  envelope and its process-fn result.

  On success: status ACCEPTED, record_id from result.
  On anomaly: status FAILED with error details."
  [{:strs [id correlation_id]} result]
  (if (error/anomaly? result)
    (->command-error id
                     correlation_id
                     (error/kind result)
                     (error/payload result))
    {"id" (str (utility/uuidv7))
     "correlation_id" correlation_id
     "causation_id" id
     "record_id" (get result "record_id" "")
     "traceparent" (telemetry/inject-traceparent)
     "tracestate" nil
     "status" "ACCEPTED"
     "payload" nil
     "error" nil}))

(defn req->command-response
  "Build a command-response from an HTTP request and a
  result.

  If result is an anomaly, builds a FAILED response.
  Otherwise passes the result through."
  [req result]
  (let [[idempotency-key correlation-id] (req->ids req)]
    (if (error/anomaly? result)
      (->command-error idempotency-key
                       correlation-id
                       (error/kind result)
                       (error/payload result))
      result)))
