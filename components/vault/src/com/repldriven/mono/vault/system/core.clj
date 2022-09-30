(ns com.repldriven.mono.vault.system.core
  (:require [com.repldriven.mono.vault.system.testcontainers-components
             :as testcontainers-components]
            [com.repldriven.mono.vault.system.components
             :as components]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.testcontainers-system.interface
             :as testcontainers-system]))

(defmulti component (fn [k _] k))

(defmethod component :default [_ v] v)

(defmethod component :container [_ v]
  (system/merge-component-config
   testcontainers-components/container v))

(defmethod component :container-api-port [_ v]
  (system/merge-component-config
   testcontainers-system/container-mapped-exposed-port v))

(defmethod component :container-api-url [_ v]
  (system/merge-component-config
   testcontainers-system/container-uri v))

(defmethod component :client [_ v]
  (system/merge-component-config
   components/client v))

(defn configure-component
  [m k v]
  (assoc m k (component k v)))

(defn configure-component-group
  [config]
  (reduce-kv configure-component {} config))

(defn configure-component-groups
  [config]
  {:vault (configure-component-group config)})

(defn configure
  [config]
  {:system/defs (configure-component-groups config)})
