(ns com.repldriven.mono.pubsub.interface
  (:refer-clojure :exclude [send])
  (:require [com.repldriven.mono.pubsub.pulsar.admin :as admin]
            [com.repldriven.mono.pubsub.pulsar.client :as client]
            [com.repldriven.mono.pubsub.pulsar.consumer :as consumer]
            [com.repldriven.mono.pubsub.pulsar.crypto]
            [com.repldriven.mono.pubsub.pulsar.env-reader :as env-reader]
            [com.repldriven.mono.pubsub.pulsar.producer :as producer]
            [com.repldriven.mono.pubsub.pulsar.reader :as reader]
            [com.repldriven.mono.pubsub.system.core :as system]
            [com.repldriven.mono.env.interface :as env]))

(defmethod env/reader 'pubsub-crypto-failure-action
  [opts tag value]
  (env-reader/crypto-failure-action opts tag value))

(defmethod env/reader 'pubsub-message-id
  [opts tag value]
  (env-reader/message-id opts tag value))

(defmethod env/reader 'pubsub-schema
  [opts tag value]
  (env-reader/schema opts tag value))

(defmethod env/reader 'pubsub-subscription-type
  [opts tag value]
  (env-reader/subscription-type opts tag value))

(defn configure-system [config] (system/configure config))

(defn send
  ([producer data] (producer/send producer data))
  ([producer data opts] (producer/send producer data opts)))

(defn send-async
  ([producer data] (producer/send-async producer data))
  ([producer data opts] (producer/send-async producer data opts)))

(defn receive
  ([consumer] (consumer/receive consumer))
  ([consumer timeout-ms] (consumer/receive consumer timeout-ms)))
