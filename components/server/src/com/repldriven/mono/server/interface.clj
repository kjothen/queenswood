(ns com.repldriven.mono.server.interface
  (:require [com.repldriven.mono.server.system.core :as system]))

(defn configure-system [config] (system/configure config))
