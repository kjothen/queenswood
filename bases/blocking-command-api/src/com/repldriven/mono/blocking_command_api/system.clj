(ns com.repldriven.mono.blocking-command-api.system
  (:require [com.repldriven.mono.ring.interface :as ring]
            [com.repldriven.mono.mqtt.interface :as mqtt]
            [com.repldriven.mono.pulsar.interface :as pulsar]))

(defmulti system (fn [k _] k))
(defmethod system :default [_ v] v)
(defmethod system :ring [_ v] (ring/create-system v))
(defmethod system :pulsar [_ v] (pulsar/create-system v))
(defmethod system :mqtt [_ v] (mqtt/create-system v))

(defn create-system [m k v] (merge-with into m (system k v)))
(defn create [config] (reduce-kv create-system {} config))

(comment
  (require '[clojure.java.io :as io]
           '[com.repldriven.mono.env.interface :as env])
  (env/set-env! (io/resource "blocking-command-api/test-env.edn") :test)
  (def system-config (create-system (:system @env/env))))