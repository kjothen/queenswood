(ns com.repldriven.mono.mqtt.interface
  (:require [com.repldriven.mono.mqtt.system.core :as system]
            [com.repldriven.mono.mqtt.client :as client]))

(defn configure-system
  [config]
  (system/configure config))

(defn publish
  [client topic payload]
  (client/publish client topic payload))

(defn subscribe
  [client topics-and-qos handler-fn]
  (client/subscribe client topics-and-qos handler-fn))
