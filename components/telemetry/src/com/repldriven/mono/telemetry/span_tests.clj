(ns com.repldriven.mono.telemetry.span-tests
  (:require
   [clojure.test :refer [is]]
   [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import
   (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
   (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)))

(defmacro with-span-tests
  "Run body under an in-memory OTel SDK, then automatically assert:
   - Each name in expected-names has a corresponding finished span
   - All finished spans share the same trace ID (W3C propagation worked)

   spans-sym is bound to a map of span-name -> SpanData after the body completes.
   Use _ if you don't need to inspect individual spans.

   Usage:
     (with-span-tests [_ [\"process-command\"]]
       (do-work))"
  [[spans-sym expected-names] & body]
  `(let [exporter# (InMemorySpanExporter/create)
         ;; Read spans inside with-open: InMemorySpanExporter.shutdown() clears
         ;; its list, so spans must be captured before the SDK closes.
         all-spans# (with-open [_sdk# (sdk/init-otel-sdk!
                                       "test"
                                       {:register-shutdown-hook false
                                        :tracer-provider {:span-processors [(SimpleSpanProcessor/create exporter#)]}})]
                      ~@body
                      (vec (.getFinishedSpanItems exporter#)))
         ~spans-sym (into {} (map (fn [s#] [(.getName s#) s#]) all-spans#))]
     (doseq [n# ~expected-names]
       (is (some? (get ~spans-sym n#))
           (str "Should have span named: " n#)))
     (let [trace-ids# (into #{} (map #(.getTraceId (.getSpanContext %)) all-spans#))]
       (is (= 1 (count trace-ids#))
           (str "All spans should share one trace ID, got: " trace-ids#)))))
