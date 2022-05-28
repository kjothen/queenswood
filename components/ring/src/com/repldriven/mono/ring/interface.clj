(ns com.repldriven.mono.ring.interface
  (:require [com.repldriven.mono.ring.system :as system]))

(defn create-system
  [config]
  (system/create config))
