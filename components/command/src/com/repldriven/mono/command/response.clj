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
  (cond
   (string? details)
   {:message details}

   (map? details)
   (into {}
         (keep (fn [[k v]]
                 (when-not (instance? Throwable v)
                   [k (if (keyword? v) (str v) v)])))
         details)

   :else
   {:message (str details)}))

(defn- ->command-envelope
  [causation-id correlation-id status payload error]
  {:id (str (utility/uuidv7))
   :correlation-id correlation-id
   :causation-id (or causation-id "")
   :traceparent (or (telemetry/inject-traceparent) "")
   :tracestate nil
   :status status
   :payload payload
   :error error})

(defn- ->command-error
  [causation-id correlation-id category details]
  (->command-envelope causation-id
                      correlation-id
                      "FAILED"
                      nil
                      (json/write-str {:category (str category)
                                       :details (serializable-details
                                                 details)})))

(defn command-response
  "Build a structured command response from a command
  envelope and its process-fn result.

  On {:status \"ACCEPTED\" :payload ...}: ACCEPTED with payload.
  On {:status \"REJECTED\" :message ...}: REJECTED with error.
  On anomaly: FAILED with error details."
  [{:keys [id correlation-id]} result]
  (cond
   (error/anomaly? result)
   (->command-error id
                    correlation-id
                    (error/kind result)
                    (error/payload result))

   (= "REJECTED" (:status result))
   (->command-envelope id
                       correlation-id
                       "REJECTED"
                       nil
                       (json/write-str {:message (:message result)}))

   :else
   (->command-envelope id
                       correlation-id
                       "ACCEPTED"
                       (:payload result)
                       nil)))

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
