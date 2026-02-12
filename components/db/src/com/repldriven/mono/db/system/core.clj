(ns com.repldriven.mono.db.system.core
  (:require
    [com.repldriven.mono.db.system.components :as components]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :db
                      {:datasources components/datasources
                       :datasource components/datasource})
