(ns com.repldriven.mono.postgres.components
  (:require [com.repldriven.mono.system.interface :as system]
            [next.jdbc]))

(def datasource
  {:start (fn [{:keys [spec]} _ _]
            (next.jdbc/get-datasource spec))
   :stop  (fn [_ _ _])
   :conf  {:spec system/required-component}})
