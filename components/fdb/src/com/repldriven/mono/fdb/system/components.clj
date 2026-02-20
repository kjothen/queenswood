(ns com.repldriven.mono.fdb.system.components
  (:require
    [com.repldriven.mono.fdb.fdb.client :as client]

    [com.repldriven.mono.system.interface :as system]))

;; ---
;; database
;; ---

(def database
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (client/create config)))
   :system/stop (fn [{:system/keys [instance]}] (client/close instance))
   :system/config {:cluster-file-path system/required-component
                   :api-version 730}})
