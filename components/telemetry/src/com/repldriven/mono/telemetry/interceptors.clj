(ns com.repldriven.mono.telemetry.interceptors
  "Interceptors for distributed tracing and request validation."
  (:require
    [sieppari.context :as sc]
    [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(def ^:private idempotency-key-re #"^[A-Za-z0-9_\-]{16,255}$")

(def require-idempotency-key
  "Interceptor that validates the `Idempotency-Key` header is present
  and syntactically well-formed (16-255 URL-safe ASCII chars).

  Returns 400 Bad Request with an RFC-9457 body if the header is
  missing or malformed."
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (let [key (get-in ctx [:request :headers "idempotency-key"])]
              (cond (nil? key)
                    (sc/terminate ctx
                                  {:status 400
                                   :body {:title "REJECTED"
                                          :type "mono/missing-idempotency-key"
                                          :status 400
                                          :detail
                                          "Missing Idempotency-Key header"}})
                    (not (re-matches idempotency-key-re key))
                    (sc/terminate
                     ctx
                     {:status 400
                      :body
                      {:title "REJECTED"
                       :type "mono/invalid-idempotency-key"
                       :status 400
                       :detail
                       "Idempotency-Key must be 16-255 URL-safe ASCII chars"}})
                    :else
                    ctx)))})

(def trace-span
  "Vector of interceptors that add OpenTelemetry server span support to HTTP requests.

  Uses clj-otel server-span-interceptors with :create-span? true, which:
  - Creates a new server span with parent extracted from incoming W3C headers
  - Sets the span as the current context so handlers can call (inject-traceparent)
  - Records HTTP response status and exceptions, ends span on leave or error

  Synchronous-only: :set-current-context? is true, which is appropriate because
  all Reitit/Sieppari interceptors and handlers run on the same thread."
  (trace-http/server-span-interceptors {:create-span? true}))
