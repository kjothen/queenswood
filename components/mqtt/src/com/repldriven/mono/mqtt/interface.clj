(ns com.repldriven.mono.mqtt.interface
  (:require
    com.repldriven.mono.mqtt.system

    [com.repldriven.mono.mqtt.core :as core]))

(defn publish [client topic payload] (core/publish client topic payload))

(defn subscribe
  [client topics-and-qos handler-fn]
  (core/subscribe client topics-and-qos handler-fn))

(defn unsubscribe [client topics] (core/unsubscribe client topics))

(defn producer [opts] (core/producer opts))

(defn consumer [opts] (core/consumer opts))
