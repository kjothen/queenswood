(ns com.repldriven.mono.bank-organization.interface
  "Organization tenant lifecycle: provisions an organization with an
  API key, default party, product, and one cash account per
  currency, and binds tier-specific policies to the new org. Read
  paths return the organization enriched with its party, accounts
  (including balances), and minted API key."
  (:require
    com.repldriven.mono.bank-organization.system

    [com.repldriven.mono.bank-organization.core :as core]
    [com.repldriven.mono.bank-organization.store :as store]))

(defn new-organization
  "Provision a new organization with an API key, party, product,
  and one cash account per currency, and bind the tier policies
  to it. Returns
  `{:organization {...} :key-secret <one-time-string>}` or an
  anomaly. The minted API key's prefix (`sk_live.` / `sk_test.`)
  tracks `org-status`; the key-secret is only returned at
  creation time.

  Args:
  - txn: FDB transaction or db handle.
  - org-name: organization display name.
  - org-type: `:organization-type-internal` or
    `:organization-type-customer`.
  - org-status: `:organization-status-*` keyword.
  - tier: tier name (string) selecting `tier=<name>`-labelled
    policies to bind to the new organization, or nil for none.
  - currencies: collection of ISO 4217 currency strings.
  - opts (optional): map; `:policies` overrides the platform
    policies used for the capability check."
  ([txn org-name org-type org-status tier currencies]
   (core/new-organization txn
                          org-name
                          org-type
                          org-status
                          tier
                          currencies))
  ([txn org-name org-type org-status tier currencies opts]
   (core/new-organization txn
                          org-name
                          org-type
                          org-status
                          tier
                          currencies
                          opts)))

(defn get-organization
  "Load a flat organization map by id. Returns the organization
  or an `:organization/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - org-id: organization id."
  [txn org-id]
  (store/get-organization txn org-id))

(defn get-organizations
  "List organizations enriched with party, accounts (with
  balances), and api-key. Returns a vector of rich organization
  maps or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - opts (optional): map; `:limit` and `:order` (default `:desc`)."
  ([txn] (core/get-organizations txn))
  ([txn opts] (core/get-organizations txn opts)))

(defn get-organizations-by-type
  "List organizations matching the given type. Returns a vector
  of organization maps or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - org-type: `:organization-type-*` keyword."
  [txn org-type]
  (core/get-organizations-by-type txn org-type))
