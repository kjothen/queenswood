(ns com.repldriven.mono.blocking-command-api.api
  (:require
    [com.repldriven.mono.blocking-command-api.commands.routes :as commands]

    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [reitit.http :as http]
    [reitit.ring :as ring]))

(defn- coercion-error-body
  "Build a command-response-shaped error body from a coercion exception.

  Mirrors the interceptor logic: idempotency-key is the command id and
  causation-id; correlation-id falls back to idempotency-key if not present.
  Checks both interceptor-set keys (available after route interceptors run)
  and raw headers (available even if request coercion fires before route
  interceptors)."
  [ex req category]
  (let [idempotency-key (or (get req :telemetry/idempotency-key)
                            (get-in req [:headers "idempotency-key"]))
        correlation-id (or (get req :telemetry/correlation-id)
                           (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    {"id" (str (java.util.UUID/randomUUID))
     "correlation_id" correlation-id
     "causation_id" idempotency-key
     "traceparent" (telemetry/inject-traceparent)
     "tracestate" nil
     "status" "error"
     "data" nil
     "error" (json/write-str {:category category
                              :details (select-keys (ex-data ex)
                                                    [:humanized :in])})}))

(def ^:private command-exception-handlers
  {:reitit.coercion/request-coercion
   (fn [ex req]
     {:status 400
      :body (coercion-error-body ex req :command/request-validation)})
   :reitit.coercion/response-coercion
   (fn [ex req]
     {:status 500
      :body (coercion-error-body ex req :command/response-coercion)})})

(defn app
  [ctx]
  (http/ring-handler (http/router (commands/routes ctx)
                                  (server/router-data
                                   command-exception-handlers))
                     (ring/create-default-handler)
                     server/standard-executor))
