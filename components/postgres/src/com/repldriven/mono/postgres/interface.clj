(ns com.repldriven.mono.postgres.interface
  (:require [com.repldriven.mono.postgres.system.core :as system]))

(defn configure-system
  [config]
  (system/configure config))
