(ns com.repldriven.mono.mqtt.system.core
  (:require [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]
            [com.repldriven.mono.mqtt.system.components :as components]
            [com.repldriven.mono.mqtt.system.testcontainers-components :as
             container-components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :mqtt
  {:container container-components/container
   :container-mapped-ports testcontainers-system/container-mapped-ports
   :container-connection-uri container-components/container-connection-uri
   :client components/client})
