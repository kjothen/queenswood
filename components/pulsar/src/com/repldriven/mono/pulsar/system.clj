(ns com.repldriven.mono.pulsar.system
  (:require [com.repldriven.mono.pulsar.local-components :as local-components]
            [com.repldriven.mono.local-system.interface :as local-system]
            [com.repldriven.mono.pulsar.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))
(defmethod component :default [_ v] v)

(defmethod component :container [_ v] (system/merge-component-config local-components/container v))
(defmethod component :container-mapped-ports [_ v] (system/merge-component-config local-system/container-mapped-ports v))
(defmethod component :container-service-http-url [_ v] (system/merge-component-config local-components/container-service-http-url v))
(defmethod component :container-service-url [_ v] (system/merge-component-config local-components/container-service-url v))

(defmethod component :admin [_ v] (system/merge-component-config components/admin v))
(defmethod component :client [_ v] (system/merge-component-config components/client v))
(defmethod component :topics [_ v] (system/merge-component-config components/topics v))
(defmethod component :reader [_ v] (system/merge-component-config components/reader v))

(defn create-component [m k v] (assoc m k (component k v)))
(defn create [config] {system/defs {:pulsar (reduce-kv create-component {} config)}})
