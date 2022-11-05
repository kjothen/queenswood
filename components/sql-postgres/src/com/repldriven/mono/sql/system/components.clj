(ns com.repldriven.mono.sql.system.components
  (:require [com.repldriven.mono.system.interface :as system]
            [next.jdbc]))

(def datasource
  {:system/start (fn [{:system/keys [config]}]
                   (next.jdbc/get-datasource config))
   :system/config  system/required-component})
