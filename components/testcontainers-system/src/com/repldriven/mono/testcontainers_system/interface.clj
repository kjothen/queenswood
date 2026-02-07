(ns com.repldriven.mono.testcontainers-system.interface
  (:require
   [com.repldriven.mono.testcontainers-system.components :as components]
   [com.repldriven.mono.testcontainers-system.vault :as vault]
   [com.repldriven.mono.testcontainers-system.mqtt :as mqtt]
   [com.repldriven.mono.testcontainers-system.pulsar :as pulsar]
   com.repldriven.mono.testcontainers-system.core))

(def mapped-exposed-port components/mapped-exposed-port)
(def mapped-ports components/mapped-ports)
(def uri components/uri)

;; Vault testcontainer components
(def vault-container vault/container)

;; MQTT testcontainer components
(def mqtt-container mqtt/container)
(def mqtt-container-connection-uri mqtt/container-connection-uri)

;; Pulsar testcontainer components
(def pulsar-container pulsar/container)

