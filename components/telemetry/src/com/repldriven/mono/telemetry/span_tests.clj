(ns com.repldriven.mono.telemetry.span-tests
  (:require
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk]

    [clojure.test :refer [is]])
  (:import
    (io.opentelemetry.api.trace Span)
    (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
    (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)))

(defonce ^:private shared-exporter (InMemorySpanExporter/create))

(defonce ^:private sdk-installed? (atom false))

(defn ensure-sdk!
  "Idempotently installs the shared in-memory OTel SDK.
  Returns the shared exporter."
  []
  (when (compare-and-set! sdk-installed? false true)
    (sdk/init-otel-sdk! "test"
                        {:register-shutdown-hook false
                         :tracer-provider {:span-processors
                                           [(SimpleSpanProcessor/create
                                             shared-exporter)]}}))
  shared-exporter)

(defmacro with-span-tests
  "Run body under an in-memory OTel SDK, then automatically
  assert:
   - Each name in expected-names has a corresponding finished span
   - All expected spans share the same trace ID

  Creates a root span to establish a trace ID, then filters
  collected spans by that trace ID. This isolates each test
  from concurrent tests sharing the same in-memory exporter.

  spans-sym is bound to a map of span-name -> SpanData after
  the body completes. Use _ if you don't need to inspect
  individual spans.

  Usage:
    (with-span-tests [_ [\"process-command\"]]
      (do-work))"
  [[spans-sym expected-names] & body]
  `(let [exporter# (ensure-sdk!)
         trace-id# (atom nil)]
     (span/with-span! ["test-root" {}]
                      (reset! trace-id# (.getTraceId (.getSpanContext
                                                      (Span/current))))
                      ~@body)
     (let [tid# @trace-id#
           all-spans# (.getFinishedSpanItems exporter#)
           test-spans# (filter #(= tid# (.getTraceId (.getSpanContext %)))
                               all-spans#)
           ~spans-sym (into {} (map (fn [s#] [(.getName s#) s#])) test-spans#)]
       (doseq [n# ~expected-names]
         (is (some? (get ~spans-sym n#)) (str "Should have span named: " n#)))
       (let [trace-ids# (into #{}
                              (map #(.getTraceId (.getSpanContext %)))
                              (vals ~spans-sym))]
         (is (= 1 (count trace-ids#))
             (str "Expected spans should share one trace ID, got: "
                  trace-ids#))))))
