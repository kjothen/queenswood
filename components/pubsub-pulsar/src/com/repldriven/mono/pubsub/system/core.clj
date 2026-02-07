(ns com.repldriven.mono.pubsub.system.core
  (:require [com.repldriven.mono.pubsub.system.components :as components]
            [com.repldriven.mono.pubsub.system.testcontainers-components :as
             testcontainers-components]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]))

;; Register pubsub components with system configurator
(defmethod system/component :pubsub/admin [_ v]
  (system/merge-component-config components/admin v))
(defmethod system/component :pubsub/client [_ v]
  (system/merge-component-config components/client v))
(defmethod system/component :pubsub/consumer [_ v]
  (system/merge-component-config components/consumer v))
(defmethod system/component :pubsub/consumers [_ v]
  (system/merge-component-config components/consumers v))
(defmethod system/component :pubsub/container [_ v]
  (system/merge-component-config testcontainers-components/container v))
(defmethod system/component :pubsub/container-service-port [_ v]
  (system/merge-component-config testcontainers-system/container-mapped-exposed-port v))
(defmethod system/component :pubsub/container-service-url [_ v]
  (system/merge-component-config testcontainers-system/container-uri v))
(defmethod system/component :pubsub/container-service-http-port [_ v]
  (system/merge-component-config testcontainers-system/container-mapped-exposed-port v))
(defmethod system/component :pubsub/container-service-http-url [_ v]
  (system/merge-component-config testcontainers-system/container-uri v))
(defmethod system/component :pubsub/crypto-key-pair-file-reader [_ v]
  (system/merge-component-config components/crypto-key-pair-file-reader v))
(defmethod system/component :pubsub/crypto-key-pair-file-readers [_ v]
  (system/merge-component-config components/crypto-key-pair-file-readers v))
(defmethod system/component :pubsub/crypto-key-pair-generator [_ v]
  (system/merge-component-config components/crypto-key-pair-generator v))
(defmethod system/component :pubsub/crypto-key-reader [_ v]
  (system/merge-component-config components/crypto-key-reader v))
(defmethod system/component :pubsub/crypto-key-readers [_ v]
  (system/merge-component-config components/crypto-key-readers v))
(defmethod system/component :pubsub/namespaces [_ v]
  (system/merge-component-config components/namespaces v))
(defmethod system/component :pubsub/producer [_ v]
  (system/merge-component-config components/producer v))
(defmethod system/component :pubsub/reader [_ v]
  (system/merge-component-config components/reader v))
(defmethod system/component :pubsub/schemas [_ v]
  (system/merge-component-config components/schemas v))
(defmethod system/component :pubsub/tenants [_ v]
  (system/merge-component-config components/tenants v))
(defmethod system/component :pubsub/topics [_ v]
  (system/merge-component-config components/topics v))
