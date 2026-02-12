(ns com.repldriven.mono.vault.system.components
  (:require
    [com.repldriven.mono.vault.client :as client]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [api-url]} config]
                         (try (log/info "Creating vault api client:" api-url)
                              (client/create api-url)
                              (catch Exception e
                                (log/error
                                 (format "Failed to create vault api client, %s"
                                         e)))))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Relinquishing vault api client")))
   :system/config {:api-url system/required-component}})
