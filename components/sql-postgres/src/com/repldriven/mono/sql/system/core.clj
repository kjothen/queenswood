(ns com.repldriven.mono.sql.system.core
  (:require [com.repldriven.mono.sql.system.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :sql
  {:datasources components/datasources
   :datasource components/datasource})
