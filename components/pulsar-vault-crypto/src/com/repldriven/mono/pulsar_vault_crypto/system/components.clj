(ns com.repldriven.mono.pulsar-vault-crypto.system.components
  (:require
    [com.repldriven.mono.pulsar-vault-crypto.core :as core]
    [com.repldriven.mono.vault.interface :as vault]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(def tenant-key-reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [client token tenant-id mount]} config]
                         (log/info
                          "Creating vault-backed crypto key reader for tenant:"
                          tenant-id)
                         (vault/authenticate-client! client :token token)
                         (core/tenant-key-reader client tenant-id mount))))
   :system/config {:client system/required-component
                   :token system/required-component
                   :tenant-id system/required-component
                   :mount "secret"}})
