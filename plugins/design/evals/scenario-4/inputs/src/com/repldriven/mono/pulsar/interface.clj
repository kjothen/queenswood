(ns com.repldriven.mono.pulsar.interface
  (:require
    [com.repldriven.mono.pulsar.core :as core]))

(defn send!
  "Sends a message on a raw Pulsar producer client."
  [producer-client topic payload]
  (core/send! producer-client topic payload))
