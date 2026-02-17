(ns com.repldriven.mono.telemetry.interface
  "Public API for telemetry operations."
  (:require
   [com.repldriven.mono.telemetry.core :as core]
   [com.repldriven.mono.telemetry.interceptors :as interceptors]
   [com.repldriven.mono.telemetry.test-fixtures :as test-fixtures]))

;; Tracing
(defmacro with-span
  "Add a span around code execution.

  Usage:
    (with-span [\"operation-name\" {:attr/key \"value\"}]
      (do-work))"
  [name-and-attrs & body]
  `(core/with-span ~name-and-attrs ~@body))

(defn with-span-parent
  "Create a span with an explicit parent context.

  Args:
  - name: Span name
  - parent-ctx: Parent OpenTelemetry context (from extract-parent-context)
  - attrs: Span attributes map
  - f: Function to execute within the span

  Returns: Result of executing f"
  [name parent-ctx attrs f]
  (core/with-span-parent name parent-ctx attrs f))

(defn add-event
  "Add an event to the current span."
  [name attrs]
  (core/add-event name attrs))

(defn set-attribute
  "Set an attribute on the current span."
  [k v]
  (core/set-attribute k v))

(defn inject-traceparent
  "Extract W3C traceparent from the current thread-local span (Span/current).

  Returns traceparent string in format: 00-{trace-id}-{span-id}-{trace-flags}
  Returns nil if no active span or OpenTelemetry is not configured."
  []
  (core/inject-traceparent))

(defn extract-parent-context
  "Extract parent OpenTelemetry context from command with traceparent/tracestate.

  Args:
  - command: Map with string keys containing \"traceparent\" and \"tracestate\" fields

  Returns: OpenTelemetry context with extracted trace information."
  [command]
  (core/extract-parent-context command))

;; Metrics
(defn counter
  "Create or get a counter instrument."
  [opts]
  (core/counter opts))

(defn inc-counter!
  "Increment a counter with attributes."
  [counter attrs]
  (core/inc-counter! counter attrs))

(defn add-counter!
  "Add a value to a counter with attributes."
  [counter value attrs]
  (core/add-counter! counter value attrs))

;; Test support
(def with-otel-test-fixture
  "Test fixture. Installs an in-memory OTel SDK for the duration of a test.
   Use with clojure.test/use-fixtures or inline as a zero-arg wrapper.
   Access recorded spans via (span-exporter)."
  test-fixtures/with-otel)

(defn span-exporter
  "Returns the in-memory span exporter bound by with-otel-test-fixture for the current test."
  []
  test-fixtures/*span-exporter*)

;; Interceptors
(def require-idempotency-key
  "Interceptor that validates Idempotency-Key header is present."
  interceptors/require-idempotency-key)

(def extract-correlation-id
  "Interceptor that extracts correlation ID from request headers."
  interceptors/extract-correlation-id)

(def trace-span
  "Vector of interceptors that add OpenTelemetry server span support to HTTP requests.
  Use with concat, not conj, when composing interceptor chains."
  interceptors/trace-span)
