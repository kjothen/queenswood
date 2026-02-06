(ns com.repldriven.mono.pubsub.system.core
  (:require [com.repldriven.mono.pubsub.system.components :as components]
            [com.repldriven.mono.pubsub.system.testcontainers-components :as
             testcontainers-components]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]))

;; Register pubsub components with system configurator
(defmethod system/component :pubsub/container [_ v]
  (system/merge-component-config testcontainers-components/container (dissoc v :annotation)))
(defmethod system/component :pubsub/container-service-port [_ v]
  (system/merge-component-config testcontainers-system/container-mapped-exposed-port (dissoc v :annotation)))
(defmethod system/component :pubsub/container-service-url [_ v]
  (system/merge-component-config testcontainers-system/container-uri (dissoc v :annotation)))
(defmethod system/component :pubsub/container-service-http-port [_ v]
  (system/merge-component-config testcontainers-system/container-mapped-exposed-port (dissoc v :annotation)))
(defmethod system/component :pubsub/container-service-http-url [_ v]
  (system/merge-component-config testcontainers-system/container-uri (dissoc v :annotation)))
(defmethod system/component :pubsub/admin [_ v]
  (system/merge-component-config components/admin (dissoc v :annotation)))
(defmethod system/component :pubsub/client [_ v]
  (system/merge-component-config components/client (dissoc v :annotation)))
(defmethod system/component :pubsub/namespaces [_ v]
  (system/merge-component-config components/namespaces (dissoc v :annotation)))
(defmethod system/component :pubsub/reader [_ v]
  (system/merge-component-config components/reader (dissoc v :annotation)))
(defmethod system/component :pubsub/schemas [_ v]
  (system/merge-component-config components/schemas (dissoc v :annotation)))
(defmethod system/component :pubsub/tenants [_ v]
  (system/merge-component-config components/tenants (dissoc v :annotation)))
(defmethod system/component :pubsub/topics [_ v]
  (system/merge-component-config components/topics (dissoc v :annotation)))

;; Legacy component multimethod for backwards compatibility
(defmulti component (fn [k _] k))

(defmethod component :default [_ v] v)

(defmethod component :container
  [_ v]
  (system/merge-component-config testcontainers-components/container v))

(defmethod component :container-service-port
  [_ v]
  (system/merge-component-config
   testcontainers-system/container-mapped-exposed-port
   v))

(defmethod component :container-service-url
  [_ v]
  (system/merge-component-config testcontainers-system/container-uri v))

(defmethod component :container-service-http-port
  [_ v]
  (system/merge-component-config
   testcontainers-system/container-mapped-exposed-port
   v))

(defmethod component :container-service-http-url
  [_ v]
  (system/merge-component-config testcontainers-system/container-uri v))

(defmethod component :admin
  [_ v]
  (system/merge-component-config components/admin v))

(defmethod component :client
  [_ v]
  (system/merge-component-config components/client v))

(defmethod component :consumers
  [_ v]
  (system/merge-component-config components/consumers v))

(defmethod component :consumer
  [_ v]
  (system/merge-component-config components/consumer v))

(defmethod component :crypto-key-pair-generator
  [_ v]
  (system/merge-component-config components/crypto-key-pair-generator v))

(defmethod component :crypto-key-pair-file-readers
  [_ v]
  (system/merge-component-config components/crypto-key-pair-file-readers v))

(defmethod component :crypto-key-pair-file-reader
  [_ v]
  (system/merge-component-config components/crypto-key-pair-file-reader v))

(defmethod component :crypto-key-readers
  [_ v]
  (system/merge-component-config components/crypto-key-readers v))

(defmethod component :crypto-key-reader
  [_ v]
  (system/merge-component-config components/crypto-key-reader v))

(defmethod component :namespaces
  [_ v]
  (system/merge-component-config components/namespaces v))

(defmethod component :producer
  [_ v]
  (system/merge-component-config components/producer v))

(defmethod component :reader
  [_ v]
  (system/merge-component-config components/reader v))

(defmethod component :schemas
  [_ v]
  (system/merge-component-config components/schemas v))

(defmethod component :tenants
  [_ v]
  (system/merge-component-config components/tenants v))

(defmethod component :topics
  [_ v]
  (system/merge-component-config components/topics v))

(defn configure-component [m k v] (assoc m k (component k v)))

(defn configure-component-group
  [config]
  (reduce-kv configure-component {} config))

(defn configure-component-groups
  [config]
  {:pubsub (configure-component-group config)})

(defn configure [config] {:system/defs (configure-component-groups config)})
