(ns com.repldriven.mono.mqtt.core
  (:require
    com.repldriven.mono.mqtt.system.core

    [com.repldriven.mono.mqtt.client :as client]))

(defn publish [c topic payload] (client/publish c topic payload))

(defn subscribe
  [c topics-and-qos handler-fn]
  (client/subscribe c topics-and-qos handler-fn))

(defn unsubscribe [c topics] (client/unsubscribe c topics))
