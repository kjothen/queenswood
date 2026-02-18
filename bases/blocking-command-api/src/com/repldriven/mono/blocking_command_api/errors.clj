(ns com.repldriven.mono.blocking-command-api.errors
  (:require
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn command-error-response
  "Build a command-response-shaped error body.

  Args:
  - idempotency-key: the originating command id, used as causation-id
  - correlation-id: the correlation chain id
  - category: error category keyword
  - details: error details map

  Returns a command-response map with status \"error\"."
  [idempotency-key correlation-id category details]
  {"id" (str (java.util.UUID/randomUUID))
   "correlation_id" correlation-id
   "causation_id" idempotency-key
   "traceparent" (telemetry/inject-traceparent)
   "tracestate" nil
   "status" "error"
   "data" nil
   "error" (json/write-str {:category category :details details})})

(defn- request->ids
  "Extract [idempotency-key correlation-id] from a request map.

  Checks interceptor-set keys first (available after route interceptors run),
  then falls back to raw headers (available even before route interceptors,
  e.g. during request coercion)."
  [req]
  (let [idempotency-key (or (get req :telemetry/idempotency-key)
                            (get-in req [:headers "idempotency-key"]))
        correlation-id (or (get req :telemetry/correlation-id)
                           (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    [idempotency-key correlation-id]))

(defn request->command-error-response
  "Build a command-response error body from a request map."
  [req category details]
  (let [[idempotency-key correlation-id] (request->ids req)]
    (command-error-response idempotency-key correlation-id category details)))

(defn coercion-ex->command-response
  "Convert a Reitit coercion exception and request to a command-response error body."
  [req category ex]
  (request->command-error-response req
                                   category
                                   (select-keys (ex-data ex) [:humanized :in])))
