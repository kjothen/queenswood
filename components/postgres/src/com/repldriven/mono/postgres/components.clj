(ns com.repldriven.mono.postgres.components
  (:require [com.repldriven.mono.system.interface :as system]
            [next.jdbc]))

(def datasource
  {:start (fn [conf _ _]
            (next.jdbc/get-datasource conf))
   :stop  (fn [_ _ _])
   :conf  system/required-component})
