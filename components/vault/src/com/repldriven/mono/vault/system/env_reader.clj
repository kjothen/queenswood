(ns com.repldriven.mono.vault.system.env-reader
  (:require [com.repldriven.mono.vault.system.components :as components]
            [com.repldriven.mono.vault.system.testcontainers-components :as
             testcontainers-components]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]))

(system/defcomponents :vault
  {:container testcontainers-components/container
   :container-api-port testcontainers-system/container-mapped-exposed-port
   :container-api-url testcontainers-system/container-uri
   :client components/client})
