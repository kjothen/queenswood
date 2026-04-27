(ns com.repldriven.mono.bank-organization.interface
  (:require
    com.repldriven.mono.bank-organization.system

    [com.repldriven.mono.bank-organization.core :as core]
    [com.repldriven.mono.bank-organization.store :as store]))

(defn new-organization
  "Creates an organization with API key, party, product,
  and one cash account per currency. Returns map or anomaly.

  `org-status` is an `:organization-status-*` keyword; the
  minted API key's prefix (`sk_live.` / `sk_test.`) is
  chosen from it. `tier-id` identifies the tier whose
  policies and limits apply. opts supports `:policies` to
  override policy resolution for the capability check."
  ([txn org-name org-type org-status tier-id currencies]
   (core/new-organization txn
                          org-name
                          org-type
                          org-status
                          tier-id
                          currencies))
  ([txn org-name org-type org-status tier-id currencies opts]
   (core/new-organization txn
                          org-name
                          org-type
                          org-status
                          tier-id
                          currencies
                          opts)))

(defn get-organization
  "Loads an organization by id. Returns the organization
  map or rejection anomaly if not found."
  [txn org-id]
  (store/get-organization txn org-id))

(defn get-organizations
  "Lists organizations enriched with party, accounts (with
  balances), and api-key. Returns a sequence of maps or
  anomaly."
  ([txn] (core/get-organizations txn))
  ([txn opts] (core/get-organizations txn opts)))

(defn get-organizations-by-type
  "Lists organizations matching the given type. Returns
  a sequence of maps or anomaly."
  [txn org-type]
  (core/get-organizations-by-type txn org-type))
