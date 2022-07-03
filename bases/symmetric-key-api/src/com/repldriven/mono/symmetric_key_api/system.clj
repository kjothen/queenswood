(ns com.repldriven.mono.symmetric-key-api.system
  (:require [com.repldriven.mono.ring.interface :as ring]))

(defmulti system (fn [k _] k))
(defmethod system :default [_ v] v)
(defmethod system :ring [_ v] (ring/configure-system v))

(defn configure-system
  [m k v]
  (merge-with into m (system k v)))

(defn configure
  [config]
  (reduce-kv configure-system {} config))
