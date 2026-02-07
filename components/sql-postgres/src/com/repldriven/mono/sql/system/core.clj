(ns com.repldriven.mono.sql.system.core
  (:require [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]
            [com.repldriven.mono.sql.system.components :as components]
            [com.repldriven.mono.sql.system.testcontainers-components :as
             testcontainers-components]
            [com.repldriven.mono.system.interface :as system]))

;; Register SQL components with system configurator
(defmethod system/component :sql/container
  [_ v]
  (system/merge-component-config testcontainers-components/container v))

(defmethod system/component :sql/container-mapped-exposed-port
  [_ v]
  (system/merge-component-config testcontainers-system/container-mapped-exposed-port v))

(defmethod system/component :sql/datasources
  [_ v]
  (system/merge-component-config components/datasources v))

(defmethod system/component :sql/datasource
  [_ v]
  (system/merge-component-config components/datasource v))

(defmulti component (fn [k _] k))

(defmethod component :default [_ v] v)

(defmethod component :container
  [_ v]
  (system/merge-component-config testcontainers-components/container v))

(defmethod component :container-mapped-exposed-port
  [_ v]
  (system/merge-component-config
   testcontainers-system/container-mapped-exposed-port
   v))

(defmethod component :datasources
  [_ v]
  (system/merge-component-config components/datasources v))

(defmethod component :datasource
  [_ v]
  (system/merge-component-config components/datasource v))

(defn configure-component [m k v] (assoc m k (component k v)))

(defn configure-component-group
  [config]
  (reduce-kv configure-component {} config))

(defn configure-component-groups
  [config]
  {:sql (configure-component-group config)})

(defn configure [config] {:system/defs (configure-component-groups config)})
