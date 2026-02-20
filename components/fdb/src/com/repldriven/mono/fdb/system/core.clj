(ns com.repldriven.mono.fdb.system.core
  (:require
    [com.repldriven.mono.fdb.system.components :as components]

    [com.repldriven.mono.system.interface :as system]))

;; system components
(system/defcomponents :fdb
                      {:cluster-file-path components/cluster-file-path
                       :db components/db})
