(ns com.repldriven.mono.sql.interface
  (:require [com.repldriven.mono.sql.system.core :as system]))

(defn configure-system
  [config]
  (system/configure config))
