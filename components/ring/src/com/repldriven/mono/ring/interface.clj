(ns com.repldriven.mono.ring.interface
  (:require [com.repldriven.mono.ring.system.core :as system]))

(defn configure-system [config] (system/configure config))
