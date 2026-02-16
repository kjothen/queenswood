(ns com.repldriven.mono.telemetry.core
  "Telemetry abstraction layer wrapping OpenTelemetry.

  Provides tracing and metrics without direct clj-otel coupling in domain code."
  (:require
   [steffan-westcott.clj-otel.api.trace.span :as span]
   [steffan-westcott.clj-otel.api.metrics.instrument :as instrument])
  (:import
   (io.opentelemetry.api.trace Span)))

(defmacro with-span
  "Add a span around code execution.

  Usage:
    (with-span [\"operation-name\" {:attr/key \"value\"}]
      (do-work))

  Falls back gracefully if OpenTelemetry is not configured."
  [name-and-attrs & body]
  `(try
     (span/with-span! ~name-and-attrs ~@body)
     (catch Exception _e#
       ;; Gracefully degrade if OTel not configured
       (do ~@body))))

(defn add-event
  "Add an event to the current span with attributes.

  No-op if no span is active or OpenTelemetry is not configured."
  [name attrs]
  (try
    (span/add-event! name attrs)
    (catch Exception _e
      ;; No-op if OTel not configured
      nil)))

(defn set-attribute
  "Set an attribute on the current span.

  No-op if no span is active or OpenTelemetry is not configured."
  [k v]
  (try
    (span/add-span-data! {:attributes {k v}})
    (catch Exception _e
      ;; No-op if OTel not configured
      nil)))

(defn counter
  "Create or get a counter instrument.

  Options:
    :name - Instrument name (required)
    :description - Human-readable description
    :unit - Unit of measurement"
  [{:keys [name] :as opts}]
  (instrument/instrument (assoc opts :instrument-type :counter)))

(defn inc-counter!
  "Increment a counter with attributes.

  Usage:
    (inc-counter! my-counter {:reason :validation-failed})"
  [counter attrs]
  (instrument/add! counter {:value 1 :attributes attrs}))

(defn add-counter!
  "Add a value to a counter with attributes.

  Usage:
    (add-counter! my-counter 5 {:operation :batch-insert})"
  [counter value attrs]
  (instrument/add! counter {:value value :attributes attrs}))

(defn inject-traceparent
  "Extract W3C traceparent from current OpenTelemetry span context.

  Returns traceparent string in format: 00-{trace-id}-{span-id}-{trace-flags}
  Returns nil if no active span or OpenTelemetry is not configured."
  []
  (try
    (let [span (Span/current)
          span-context (.getSpanContext span)]
      (when (.isValid span-context)
        (format "00-%s-%s-%02x"
                (.getTraceId span-context)
                (.getSpanId span-context)
                (if (.isSampled span-context) 1 0))))
    (catch Exception _e
      nil)))
