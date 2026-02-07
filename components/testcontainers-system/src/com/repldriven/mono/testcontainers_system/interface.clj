(ns com.repldriven.mono.testcontainers-system.interface
  (:require com.repldriven.mono.testcontainers-system.core
            [com.repldriven.mono.testcontainers-system.components :as
             components]))

(def container components/container)
(def container-mapped-ports components/mapped-ports)
(def container-mapped-exposed-port components/mapped-exposed-port)
(def container-uri components/uri)
