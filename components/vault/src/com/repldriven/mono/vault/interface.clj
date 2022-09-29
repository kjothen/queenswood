(ns com.repldriven.mono.vault.interface
  (:require [com.repldriven.mono.vault.client :as client]
            [com.repldriven.mono.vault.system.core :as system]))

(defn configure-system
  [config]
  (system/configure config))

(defn read-secret
  [client mount path & opts]
  (if (some? opts)
    (client/read-secret client mount path opts)
    (client/read-secret client mount path)))
