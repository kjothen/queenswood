(ns com.repldriven.mono.ring.system.core
  (:require [com.repldriven.mono.ring.system.embedded-components :as embedded-components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))
(defmethod component :default [_ v] v)
(defmethod component :jetty-adapter [_ v] (system/merge-component-config embedded-components/jetty-adapter v))

(defn configure-component
  [m k v]
  (assoc m k (component k v)))

(defn configure-component-group
  [config]
  (reduce-kv configure-component {} config))

(defn configure-component-groups
  [config]
  {:ring (configure-component-group config)})

(defn configure
  [config]
  {:system/defs (configure-component-groups config)})
