(ns com.repldriven.mono.pulsar.core
  (:refer-clojure :exclude [read send])
  (:require
    [com.repldriven.mono.pulsar.pulsar.admin :as admin]
    [com.repldriven.mono.pulsar.pulsar.consumer :as consumer]
    [com.repldriven.mono.pulsar.pulsar.producer :as producer]
    [com.repldriven.mono.pulsar.pulsar.reader :as reader]
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]))

;;;; lifecycle

(defn producer [opts] (producer/create opts))

(defn consumer
  "Creates a consumer and returns a map with the Java Consumer
  instance and its resolved schema for later deserialization."
  [opts]
  {:instance (consumer/create opts)
   :schema (when-let [s (get-in opts [:conf :schema])]
             (schemas/resolve (:schemas opts) s))})

;;;; producer
(defn send
  ([producer data] (producer/send producer data))
  ([producer data opts] (producer/send producer data opts)))

(defn send-async
  ([producer data] (producer/send-async producer data))
  ([producer data opts] (producer/send-async producer data opts)))

;;;; consumer
(defn receive
  [consumer schema timeout-ms]
  (consumer/receive consumer schema timeout-ms))

(defn acknowledge [consumer message] (consumer/acknowledge consumer message))

;;;; reader
(defn read [reader schema timeout-ms] (reader/read reader schema timeout-ms))

;;;; schema
(defn schema->avro [schema] (schemas/schema->avro schema))

;;;; admin
(defn admin-namespace-url
  [admin tenant namespace]
  (admin/namespace-url admin tenant namespace))
