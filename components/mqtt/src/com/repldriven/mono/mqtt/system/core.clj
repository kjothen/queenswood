(ns com.repldriven.mono.mqtt.system.core
  (:require [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]
            [com.repldriven.mono.mqtt.system.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :mqtt
  {:container testcontainers-system/mqtt-container
   :container-mapped-ports testcontainers-system/mapped-ports
   :container-connection-uri testcontainers-system/mqtt-container-connection-uri
   :client components/client})
