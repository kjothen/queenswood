(ns com.repldriven.mono.telemetry.interceptors
  "Interceptors for distributed tracing and request validation."
  (:require
    [com.repldriven.mono.log.interface :as log]

    [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(def require-idempotency-key
  "Interceptor that validates Idempotency-Key header is present.

  Extracts the header and adds it to request context as :telemetry/idempotency-key.
  Returns 400 Bad Request if header is missing."
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (log/debugf
             "telemetry.interceptors/require-idempotency-key(enter): [headers=]"
             (get-in ctx [:request :headers]))
            (let [idem-key (get-in ctx [:request :headers "idempotency-key"])]
              (if (some? idem-key)
                (assoc-in ctx [:request :telemetry/idempotency-key] idem-key)
                (assoc ctx
                       :response
                       {:status 400
                        :body {:error "Missing Idempotency-Key header"}}))))})

(def extract-correlation-id
  "Interceptor that extracts correlation ID from request headers.

  Looks for Correlation-ID header first, falls back to Idempotency-Key if not found.
  Adds the correlation ID to request context as :telemetry/correlation-id."
  {:name ::extract-correlation-id
   :enter
   (fn [ctx]
     (log/debugf
      "telemetry.interceptors/extract-correlation-id(enter): [headers=]"
      (get-in ctx [:request :headers]))
     (let [correlation-id (or (get-in ctx [:request :headers "correlation-id"])
                              (get-in ctx
                                      [:request :telemetry/idempotency-key]))]
       (assoc-in ctx [:request :telemetry/correlation-id] correlation-id)))})

(def trace-span
  "Vector of interceptors that add OpenTelemetry server span support to HTTP requests.

  Uses clj-otel server-span-interceptors with :create-span? true, which:
  - Creates a new server span with parent extracted from incoming W3C headers
  - Sets the span as the current context so handlers can call (inject-traceparent)
  - Records HTTP response status and exceptions, ends span on leave or error

  Synchronous-only: :set-current-context? is true, which is appropriate because
  all Reitit/Sieppari interceptors and handlers run on the same thread."
  (trace-http/server-span-interceptors {:create-span? true}))
