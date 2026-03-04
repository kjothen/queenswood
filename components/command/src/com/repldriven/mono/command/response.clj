(ns com.repldriven.mono.command.response
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]))

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
  [causation-id correlation-id status payload reason message]
  {:id (str (utility/uuidv7))
   :correlation-id correlation-id
   :causation-id (or causation-id "")
   :traceparent (or (telemetry/inject-traceparent) "")
   :tracestate nil
   :status status
   :payload payload
   :reason reason
   :message message})

(defn- ->command-rejection
  [causation-id correlation-id category details]
  (->command-envelope causation-id
                      correlation-id
                      "REJECTED"
                      nil
                      (str category)
                      (:message (serializable-details details))))

(defn- ->command-error
  [causation-id correlation-id category details]
  (->command-envelope causation-id
                      correlation-id
                      "FAILED"
                      nil
                      (str category)
                      (:message (serializable-details details))))

(defn command-response
  "Build a structured command response from a command
  envelope and its process-fn result.

  On rejection anomaly: REJECTED with reason and message.
  On error anomaly: FAILED with reason and message.
  On {:status \"ACCEPTED\" :payload ...}: ACCEPTED with
    payload."
  [{:keys [id correlation-id]} result]
  (cond
   (error/rejection? result)
   (->command-rejection id
                        correlation-id
                        (error/kind result)
                        (error/payload result))

   (error/error? result)
   (->command-error id
                    correlation-id
                    (error/kind result)
                    (error/payload result))

   :else
   (->command-envelope id
                       correlation-id
                       "ACCEPTED"
                       (:payload result)
                       nil
                       nil)))

(defn req->command-response
  "Build a command-response from an HTTP request and a
  result.

  If result is a rejection anomaly, builds a REJECTED
  response. If an error anomaly, builds a FAILED response.
  Otherwise passes the result through."
  [req result]
  (let [[idempotency-key correlation-id] (req->ids req)]
    (cond
     (error/rejection? result)
     (->command-rejection idempotency-key
                          correlation-id
                          (error/kind result)
                          (error/payload result))

     (error/error? result)
     (->command-error idempotency-key
                      correlation-id
                      (error/kind result)
                      (error/payload result))

     :else
     result)))
