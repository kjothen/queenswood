(ns com.repldriven.mono.pubsub.interface
  (:require [com.repldriven.mono.pulsar.interface :as pulsar]))

(defn ensure-topic
  [admin topic-name & opts]
  (if (some? opts)
    (pulsar/ensure-topic admin topic-name opts)
    (pulsar/ensure-topic admin topic-name)))

(defn publish
  [client topic-name message]
  (pulsar/publish client topic-name message))

(defn subscribe
  [client topic-name f]
  (pulsar/subscribe client topic-name f))
