(ns com.repldriven.mono.pulsar.interface
  (:refer-clojure :exclude [read send])
  (:require
   com.repldriven.mono.pulsar.system.core

   [com.repldriven.mono.pulsar.pulsar.consumer :as consumer]
   [com.repldriven.mono.pulsar.pulsar.producer :as producer]
   [com.repldriven.mono.pulsar.pulsar.reader :as reader]))

;;;; producer
(defn send
  ([producer data] (producer/send producer data))
  ([producer data opts] (producer/send producer data opts)))

(defn send-async
  ([producer data] (producer/send-async producer data))
  ([producer data opts] (producer/send-async producer data opts)))

;;;; consumer
(defn receive
  ([consumer] (consumer/receive consumer))
  ([consumer timeout-ms] (consumer/receive consumer timeout-ms)))

;;;; reader
(defn read
  [reader schema timeout-ms]
  (reader/read reader schema timeout-ms))
