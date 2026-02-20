(ns com.repldriven.mono.fdb.system.components
  (:require
    [com.repldriven.mono.fdb.fdb.client :as client]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

;; ---
;; database
;; ---

(def database
  {:system/start (fn [{:system/keys [config instance]}]
                   (let [_ (log/info "FDB database start called, instance:"
                                     instance
                                     "config:" config)]
                     (or instance (client/create config))))
   :system/stop (fn [{:system/keys [instance]}] (client/close instance))
   :system/config {:cluster-file-path system/required-component
                   :api-version 730}})
