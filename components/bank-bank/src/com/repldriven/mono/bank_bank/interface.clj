(ns com.repldriven.mono.bank-bank.interface
  "Bank tenant lifecycle: provisions a bank with a service-account
  client, an organization party, and a default chart of bank-owned
  ledger accounts per currency, and binds tier-specific policies to
  the new bank. Read paths return the bank enriched with its party and
  accounts (with balances)."
  (:require
    [com.repldriven.mono.bank-bank.core :as core]
    [com.repldriven.mono.bank-bank.store :as store]))

(defn new-bank
  "Provision a new bank with a service-account client, an organization
  party, and a default chart of ledger accounts per currency, and bind
  the tier policies to it. Returns
  `{:bank {... :client-id …} :client-secret <one-time-string>}`
  or an anomaly. The client_secret is only returned at creation time.

  Args:
  - txn: FDB transaction or db handle.
  - bank-name: bank display name.
  - bank-status: `:bank-status-*` keyword.
  - tier: tier name (string) selecting `tier=<name>`-labelled
    policies to bind to the new bank, or nil for none.
  - currencies: collection of ISO 4217 currency strings.
  - opts: map; `:identity-provider` (required) is the IDP component
    that issues the bank's service-account client — without one a bank
    has no credentials, so creation is rejected `:bank/missing-identity-provider`;
    `:audience` (string) is the `aud` claim stamped on tokens minted
    for the new client; `:policies` overrides the platform policies
    used for the capability check."
  [txn bank-name bank-status tier currencies opts]
  (core/new-bank txn
                 bank-name
                 bank-status
                 tier
                 currencies
                 opts))

(defn get-bank
  "Load a flat bank map by id. Returns the bank or a
  `:bank/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: bank id."
  [txn bank-id]
  (store/get-bank txn bank-id))

(defn get-banks
  "List banks enriched with party and accounts (with balances).
  Returns a vector of rich bank maps or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - opts (optional): map; `:limit` and `:order` (default `:desc`)."
  ([txn] (core/get-banks txn))
  ([txn opts] (core/get-banks txn opts)))
