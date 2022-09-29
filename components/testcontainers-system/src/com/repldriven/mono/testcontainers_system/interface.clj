(ns com.repldriven.mono.testcontainers-system.interface
  (:require [com.repldriven.mono.testcontainers-system.components :as components]
            [com.repldriven.mono.testcontainers-system.core :as system]))

(defn configure-system
  [config name]
  (system/configure config name))

(def container components/container)
(def container-mapped-ports components/mapped-ports)
(def container-mapped-exposed-port components/mapped-exposed-port)
(def container-uri components/uri)
