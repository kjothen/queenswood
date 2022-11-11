(ns com.repldriven.mono.sql.system.components
  (:require [com.repldriven.mono.system.interface :as system]
            [next.jdbc]))

(def datasources
  {:system/start (fn [{:system/keys [config]}]
                   (reduce-kv (fn [m k v]
                                (assoc m k (next.jdbc/get-datasource v)))
                              {}
                              config))
   :system/config system/required-component})

(def datasource
  {:system/start (fn [{:system/keys [config]}] config)
   :system/config system/required-component})
