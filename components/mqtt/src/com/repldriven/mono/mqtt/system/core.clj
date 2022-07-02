(ns com.repldriven.mono.mqtt.system.core
  (:require [com.repldriven.mono.testcontainers-system.interface :as testcontainers-system]
            [com.repldriven.mono.mqtt.system.components :as components]
            [com.repldriven.mono.mqtt.system.testcontainers-components :as container-components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))
(defmethod component :default [_ v] v)
(defmethod component :container [_ v] (system/merge-component-config container-components/container v))
(defmethod component :container-mapped-ports [_ v] (system/merge-component-config testcontainers-system/container-mapped-ports v))
(defmethod component :container-connection-uri [_ v] (system/merge-component-config container-components/container-connection-uri v))
(defmethod component :client [_ v] (system/merge-component-config components/client v))

(defn configure-component [m k v] (assoc m k (component k v)))
(defn configure-component-group [config] (reduce-kv configure-component {} config))
(defn configure-component-groups [config] {:mqtt (configure-component-group config)})

(defn configure [config] {system/defs (configure-component-groups config)})
