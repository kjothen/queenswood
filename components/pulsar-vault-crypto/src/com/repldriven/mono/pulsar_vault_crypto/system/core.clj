(ns com.repldriven.mono.pulsar-vault-crypto.system.core
  (:require
    [com.repldriven.mono.pulsar-vault-crypto.system.components :as components]
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :pulsar-vault-crypto
                      {:tenant-key-reader components/tenant-key-reader})
