(ns com.repldriven.mono.bank-api-key.interface
  "API key issuance and lookup for organizations. Issuance returns
  the secret only at creation time and stores a redacted, hashed
  record; lookup and listing always return the redacted form. Policy
  checks gate both issuance capability and per-org count limits."
  (:require
    [com.repldriven.mono.bank-api-key.core :as core]
    [com.repldriven.mono.bank-api-key.store :as store]))

(defn new-api-key
  "Issue a new API key for an organization. Returns
  `{:api-key <map> :key-secret <string>}` or an anomaly. The
  key-secret is only available at creation time; the persisted
  record stores its hash. The key prefix tracks `status` —
  `sk_live.` for live orgs, `sk_test.` otherwise.

  Args:
  - txn: FDB transaction or db handle.
  - org-id: owning organization id.
  - status: organization status keyword (`:organization-status-live`
    or `:organization-status-test`); selects the key prefix.
  - key-name: human-readable label for the key.
  - opts (optional): map; `:policies` overrides policy resolution
    for the capability/limit check."
  ([txn org-id status key-name]
   (core/new-api-key txn org-id status key-name))
  ([txn org-id status key-name opts]
   (core/new-api-key txn org-id status key-name opts)))

(defn get-api-key
  "Look up an API key by its hash. Returns a redacted ApiKey map
  (no `:key-hash`) or an `:api-key/not-found` anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - key-hash: hash of the key secret."
  [txn key-hash]
  (store/get-api-key txn key-hash))

(defn get-api-keys
  "List all API keys for an organization. Returns a vector of
  redacted ApiKey maps, newest-first by default.

  Args:
  - txn: FDB transaction or db handle.
  - org-id: owning organization id.
  - opts (optional): map; `:order` is `:desc` (default) or `:asc`."
  ([txn org-id] (store/get-api-keys txn org-id))
  ([txn org-id opts] (store/get-api-keys txn org-id opts)))
