(ns com.repldriven.mono.pulsar.interface
  (:refer-clojure :exclude [read send])
  (:require
    com.repldriven.mono.pulsar.system.core

    [com.repldriven.mono.pulsar.core :as core]))

;;;; producer
(defn send
  ([producer data] (core/send producer data))
  ([producer data opts] (core/send producer data opts)))

(defn send-async
  ([producer data] (core/send-async producer data))
  ([producer data opts] (core/send-async producer data opts)))

;;;; consumer
(defn receive
  [consumer schema timeout-ms]
  (core/receive consumer schema timeout-ms))

(defn acknowledge [consumer message] (core/acknowledge consumer message))

;;;; reader
(defn read [reader schema timeout-ms] (core/read reader schema timeout-ms))

;;;; schema
(defn schema->avro [schema] (core/schema->avro schema))

;;;; admin
(defn admin-namespace-url
  [admin tenant namespace]
  (core/admin-namespace-url admin tenant namespace))
