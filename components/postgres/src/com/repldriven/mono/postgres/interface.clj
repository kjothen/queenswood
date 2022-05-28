(ns com.repldriven.mono.postgres.interface
  (:require [com.repldriven.mono.postgres.system :as system]))

(defn create-system
  [config]
  (system/create config))
