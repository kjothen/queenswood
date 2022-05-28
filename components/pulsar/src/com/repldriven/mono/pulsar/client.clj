(ns com.repldriven.mono.pulsar.client
  (:import (org.apache.pulsar.client.api PulsarClient)))

(defn publish
  [^PulsarClient client topic-name message])

(defn subscribe
  [^PulsarClient client topic-name f])
