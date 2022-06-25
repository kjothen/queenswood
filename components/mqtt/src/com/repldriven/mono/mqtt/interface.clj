(ns com.repldriven.mono.mqtt.interface
  (:require [com.repldriven.mono.mqtt.system :as system]
            [com.repldriven.mono.mqtt.client :as client]
            [com.repldriven.mono.env.interface :as env]))

(defn create-system
  [config]
  (system/create config))

(defn publish
  [client topic payload]
  (client/publish client topic payload))

(defn subscribe
  [client topics-and-qos handler-fn]
  (client/subscribe client topics-and-qos handler-fn))