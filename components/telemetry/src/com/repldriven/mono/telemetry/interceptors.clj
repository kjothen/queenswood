(ns com.repldriven.mono.telemetry.interceptors
  "Interceptors for distributed tracing and request validation."
  (:require
   [com.repldriven.mono.log.interface :as log]

   [steffan-westcott.clj-otel.api.trace.span :as span]
   [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(def require-idempotency-key
  "Interceptor that validates Idempotency-Key header is present.

  Extracts the header and adds it to request context as :telemetry/idempotency-key.
  Returns 400 Bad Request if header is missing."
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (log/debugf "telemetry.interceptors/require-idempotency-key(enter): [headers=]" (get-in ctx [:request :headers]))
            (let [idem-key (get-in ctx [:request :headers "idempotency-key"])]
              (if (some? idem-key)
                (assoc-in ctx [:request :telemetry/idempotency-key] idem-key)
                (assoc ctx :response
                       {:status 400
                        :body {:error "Missing Idempotency-Key header"}}))))})

(def extract-correlation-id
  "Interceptor that extracts correlation ID from request headers.

  Looks for Correlation-ID header first, falls back to Idempotency-Key if not found.
  Adds the correlation ID to request context as :telemetry/correlation-id."
  {:name ::extract-correlation-id
   :enter (fn [ctx]
            (log/debugf "telemetry.interceptors/extract-correlation-id(enter): [headers=]" (get-in ctx [:request :headers]))
            (let [correlation-id (or (get-in ctx [:request :headers "correlation-id"])
                                     (get-in ctx [:request :telemetry/idempotency-key]))]
              (assoc-in ctx [:request :telemetry/correlation-id] correlation-id)))})

(defn- server-span-interceptor
  "Creates an interceptor that adds OpenTelemetry server span support.

  Uses clj-otel span-interceptor to manage span lifecycle automatically.
  W3C trace context from incoming HTTP headers is automatically extracted."
  [span-name]
  (span/span-interceptor
   ::span-context
   (fn [ctx]
     (let [request (:request ctx)]
       (trace-http/server-span-opts
        {:name (or span-name
                   (str (name (:request-method request))
                        " " (:uri request)))
         :kind :server})))))

(def trace-span
  "Interceptor that creates OpenTelemetry spans for HTTP requests.

  Creates server spans using OpenTelemetry SDK. W3C trace context from incoming
  HTTP headers is automatically extracted and propagated.

  Records HTTP status code and exceptions, properly ends span on :leave or :error."
  (server-span-interceptor nil))
