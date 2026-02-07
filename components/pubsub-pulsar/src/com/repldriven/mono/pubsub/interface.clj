(ns com.repldriven.mono.pubsub.interface
  (:refer-clojure :exclude [send])
  (:require [com.repldriven.mono.pubsub.pulsar.admin]
            [com.repldriven.mono.pubsub.pulsar.client]
            [com.repldriven.mono.pubsub.pulsar.consumer :as consumer]
            [com.repldriven.mono.pubsub.pulsar.crypto]
            [com.repldriven.mono.pubsub.system.env-reader]
            [com.repldriven.mono.pubsub.pulsar.producer :as producer]
            [com.repldriven.mono.pubsub.pulsar.reader]
            [com.repldriven.mono.pubsub.system.core]))

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
