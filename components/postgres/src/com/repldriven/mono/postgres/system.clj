(ns com.repldriven.mono.postgres.system
  (:require [com.repldriven.mono.local-system.interface :as local-system]
            [com.repldriven.mono.postgres.components :as components]
            [com.repldriven.mono.postgres.local-components :as local-components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))
(defmethod component :default [_ v] v)
(defmethod component :container [_ v] (system/merge-component-config local-components/container v))
(defmethod component :container-mapped-exposed-port [_ v] (system/merge-component-config local-system/container-mapped-exposed-port v))
(defmethod component :datasource [_ v] (system/merge-component-config components/datasource v))

(defn create-component [m k v] (assoc m k (component k v)))
(defn create [config] {system/defs {:postgres (reduce-kv create-component {} config)}})
