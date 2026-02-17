(ns com.repldriven.mono.telemetry.test-fixtures
  (:require
   [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import
   (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
   (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)))

(def ^:dynamic *span-exporter* nil)

(defn with-otel
  "Test fixture that installs an in-memory OpenTelemetry SDK and binds
   *span-exporter* for the duration of f.

   Usage as a clojure.test fixture:
     (use-fixtures :each telemetry/with-otel)

   Usage inline:
     (with-otel
       (fn []
         (sut/do-work)
         (is (seq (.getFinishedSpanItems *span-exporter*)))))"
  [f]
  (let [exporter (InMemorySpanExporter/create)]
    (binding [*span-exporter* exporter]
      (with-open [_sdk (sdk/init-otel-sdk!
                        "test"
                        {:register-shutdown-hook false
                         :tracer-provider {:span-processors [(SimpleSpanProcessor/create exporter)]}})]
        (f)))))
