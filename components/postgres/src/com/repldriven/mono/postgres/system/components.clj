(ns com.repldriven.mono.postgres.system.components
  (:require [com.repldriven.mono.system.interface :as system]
            [next.jdbc]))

(def datasource
  {:system/start (fn [{:keys [config]}]
                   (next.jdbc/get-datasource config))
   :system/config  system/required-component})
