(ns com.repldriven.mono.bank-organization.interface
  (:require
    com.repldriven.mono.bank-organization.system

    [com.repldriven.mono.bank-organization.core :as core]
    [com.repldriven.mono.bank-organization.store :as store]))

(defn new-organization
  "Creates an organization with API key, party, product,
  and one cash account per currency. Returns map or anomaly."
  [txn org-name org-type tier-type currencies]
  (core/new-organization txn org-name org-type tier-type currencies))

(defn get-organization
  "Loads an organization by id. Returns the organization
  map or rejection anomaly if not found."
  [txn org-id]
  (store/get-organization txn org-id))

(defn get-organizations
  "Lists organizations enriched with party, accounts (with balances),
  and api-key. Returns a sequence of maps or anomaly."
  [txn]
  (core/get-organizations txn))

(defn get-organizations-by-type
  "Lists organizations matching the given type. Returns
  a sequence of maps or anomaly."
  [txn org-type]
  (core/get-organizations-by-type txn org-type))
