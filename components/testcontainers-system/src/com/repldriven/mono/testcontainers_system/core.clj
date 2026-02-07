(ns com.repldriven.mono.testcontainers-system.core
  (:require [com.repldriven.mono.testcontainers-system.components :as
             components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :testcontainers-system
  {:container components/container
   :container-mapped-ports components/mapped-ports
   :container-mapped-exposed-port components/mapped-exposed-port
   :container-uri components/uri})
