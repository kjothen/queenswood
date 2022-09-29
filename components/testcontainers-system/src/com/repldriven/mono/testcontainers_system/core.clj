(ns com.repldriven.mono.testcontainers-system.core
  (:require [com.repldriven.mono.testcontainers-system.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(defmulti component (fn [k _] k))

(defmethod component :default [_ v] v)

(defmethod component :container [_ v]
  (system/merge-component-config components/container v))

(defmethod component :container-mapped-ports [_ v]
  (system/merge-component-config components/mapped-ports v))

(derive ::container-mapped-exposed-port-1 ::container-mapped-exposed-port)
(derive ::container-mapped-exposed-port-2 ::container-mapped-exposed-port)
(derive ::container-mapped-exposed-port-3 ::container-mapped-exposed-port)
(defmethod component :container-mapped-exposed-port [_ v]
  (system/merge-component-config components/mapped-exposed-port v))

(derive ::container-uri-1 ::container-uri)
(derive ::container-uri-2 ::container-uri)
(derive ::container-uri-3 ::container-uri)
(defmethod component :container-uri [_ v]
  (system/merge-component-config components/uri v))

(defn configure-component
  [m k v]
  (assoc m k (component k v)))

(defn configure-component-group
  [config]
  (reduce-kv configure-component {} config))

(defn configure-component-groups
  [config name]
  {name (configure-component-group config)})

(defn configure
  [config name]
  {:system/defs (configure-component-groups config name)})
