(ns com.repldriven.mono.bank-bank.interface
  "Bank write side: provisions a bank with a service-account client,
  an organization party, a default chart of bank-owned ledger accounts
  per currency, tier-specific policy bindings, and (onboarding) the
  owner membership — all in one transaction.

  Reads live in `bank-bank-query`; `bank-api` requires the query
  brick, not this one — bank creation reaches the processor as a
  command over the bus."
  (:require
    com.repldriven.mono.bank-bank.system

    [com.repldriven.mono.bank-bank.core :as core]))

(defn new-bank
  "Provision a new bank with a service-account client, an organization
  party, and a default chart of ledger accounts per currency, and bind
  the tier policies to it. When `:membership` is supplied, also create
  the owner membership in the same transaction. Returns
  `{:bank {…} :membership <map-or-nil>}` or an anomaly. The
  service-account secret is not returned — callers needing one mint it
  via `identity-provider/rotate-secret` after creation.

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
    for the new client; `:company-binding` (map, optional) is the
    confirmed legal-entity snapshot to bind the bank to (onboarding) —
    creation is rejected `:onboarding/company-not-active` unless its
    `:company-status` is active; `:membership` (map, optional) is
    `{:user-id … :role …}` for the owner membership — rejected
    `:membership/already-exists` when the user already belongs to a
    bank; `:policies` overrides the platform policies used for the
    capability check."
  [txn bank-name bank-status tier currencies opts]
  (core/new-bank txn
                 bank-name
                 bank-status
                 tier
                 currencies
                 opts))
