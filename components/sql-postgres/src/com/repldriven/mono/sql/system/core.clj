(ns com.repldriven.mono.sql.system.core
  (:require [com.repldriven.mono.testcontainers-system.interface :as
             testcontainers-system]
            [com.repldriven.mono.sql.system.components :as components]
            [com.repldriven.mono.sql.system.testcontainers-components :as
             testcontainers-components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :sql
  {:container testcontainers-components/container
   :container-mapped-exposed-port testcontainers-system/container-mapped-exposed-port
   :datasources components/datasources
   :datasource components/datasource})
