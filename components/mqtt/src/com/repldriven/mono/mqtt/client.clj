(ns com.repldriven.mono.mqtt.client
  (:require [clojurewerkz.machine-head.client :as mh]))

(defn publish
  [client topic payload]
  (mh/publish client topic payload))

(defn subscribe
  [client topics-and-qos handler-fn]
  (mh/subscribe client topics-and-qos handler-fn))