(ns com.repldriven.mono.mqtt.system
  (:require [com.repldriven.mono.mqtt.local-components :as local-components]
            [com.repldriven.mono.local-system.interface :as local-system]
            [com.repldriven.mono.mqtt.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))
(defmethod component :default [_ v] v)
(defmethod component :container [_ v] (system/merge-component-config local-components/container v))
(defmethod component :container-mapped-ports [_ v] (system/merge-component-config local-system/container-mapped-ports v))
(defmethod component :container-connection-uri [_ v] (system/merge-component-config local-components/container-connection-uri v))
(defmethod component :client [_ v] (system/merge-component-config components/client v))

(defn create-component [m k v] (assoc m k (component k v)))
(defn create [config] {system/defs {:mqtt (reduce-kv create-component {} config)}})
