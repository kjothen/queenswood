(ns com.repldriven.mono.bank-api-key.interface
  (:require
    [com.repldriven.mono.bank-api-key.core :as core]
    [com.repldriven.mono.bank-api-key.store :as store]))

(defn new-api-key
  "Creates a new ApiKey record map and its key secret.
  Returns {:api-key <map> :key-secret <string>} or anomaly.
  The key-secret is only available at creation time. `status`
  is the owning organization's status keyword (live / test)
  and selects the key prefix. opts supports `:policies` to
  override policy resolution for the capability check."
  ([txn org-id status key-name]
   (core/new-api-key txn org-id status key-name))
  ([txn org-id status key-name opts]
   (core/new-api-key txn org-id status key-name opts)))

(defn get-api-key
  "Looks up an API key by its hash. Returns the ApiKey map
  or nil."
  [txn key-hash]
  (store/get-api-key txn key-hash))

(defn get-api-keys
  "Lists all API keys for a given organization. Returns a
  sequence of ApiKey maps."
  ([txn org-id] (store/get-api-keys txn org-id))
  ([txn org-id opts] (store/get-api-keys txn org-id opts)))
