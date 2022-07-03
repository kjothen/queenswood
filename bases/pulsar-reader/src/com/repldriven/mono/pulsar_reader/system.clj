(ns com.repldriven.mono.pulsar-reader.system
  (:require [com.repldriven.mono.pulsar.interface :as pulsar]))

(defmulti system (fn [k _] k))
(defmethod system :default [_ v] v)
(defmethod system :pulsar [_ v] (pulsar/configure-system v))

(defn configure-system
  [m k v]
  (merge-with into m (system k v)))

(defn configure
  [config]
  (reduce-kv configure-system {} config))