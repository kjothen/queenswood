(ns com.repldriven.mono.pulsar.system.core
  (:require [com.repldriven.mono.pulsar.system.components :as components]
            [com.repldriven.mono.pulsar.system.testcontainers-components
             :as testcontainers-components]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.testcontainers-system.interface
             :as testcontainers-system]))

(defmulti component (fn [k _] k))

(defmethod component :default [_ v] v)

(defmethod component :container [_ v]
  (system/merge-component-config
    testcontainers-components/container v))

(defmethod component :container-service-port [_ v]
  (system/merge-component-config
    testcontainers-system/container-mapped-exposed-port v))

(defmethod component :container-service-url [_ v]
  (system/merge-component-config
    testcontainers-system/container-uri v))

(defmethod component :container-service-http-port [_ v]
  (system/merge-component-config
    testcontainers-system/container-mapped-exposed-port v))

(defmethod component :container-service-http-url [_ v]
  (system/merge-component-config
    testcontainers-system/container-uri v))

(defmethod component :admin [_ v]
  (system/merge-component-config components/admin v))

(defmethod component :client [_ v]
  (system/merge-component-config components/client v))

(defmethod component :consumer [_ v]
  (system/merge-component-config components/consumer v))

(defmethod component :consumer-2 [_ v]
  (system/merge-component-config components/consumer v))

(defmethod component :crypto-key-pair-generator [_ v]
  (system/merge-component-config components/crypto-key-pair-generator v))

(defmethod component :crypto-key-pair-file-reader [_ v]
  (system/merge-component-config components/crypto-key-pair-file-reader v))

(defmethod component :crypto-key-pair-file-reader-2 [_ v]
  (system/merge-component-config components/crypto-key-pair-file-reader v))

(defmethod component :crypto-key-reader [_ v]
  (system/merge-component-config components/crypto-key-reader v))

(defmethod component :crypto-key-reader-2 [_ v]
  (system/merge-component-config components/crypto-key-reader v))

(defmethod component :topics [_ v]
  (system/merge-component-config components/topics v))

(defmethod component :producer [_ v]
  (system/merge-component-config components/producer v))

(defmethod component :reader [_ v]
  (system/merge-component-config components/reader v))

(defmethod component :schemas [_ v]
  (system/merge-component-config components/schemas v))

(defn configure-component
  [m k v]
  (assoc m k (component k v)))

(defn configure-component-group
  [config]
  (reduce-kv configure-component {} config))

(defn configure-component-groups
  [config]
  {:pulsar (configure-component-group config)})

(defn configure
  [config]
  {:system/defs (configure-component-groups config)})
