(ns com.repldriven.mono.ring.system
  (:require [com.repldriven.mono.ring.embedded-components :as embedded-components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))
(defmethod component :default [_ v] v)
(defmethod component :jetty-adapter [_ v] (system/merge-component-config embedded-components/jetty-adapter v))
(defn create-component [m k v] (assoc m k (component k v)))
(defn create [config] {system/defs {:ring (reduce-kv create-component {} config)}})
