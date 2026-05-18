(ns com.repldriven.mono.bank-user.interface
  "Platform-identity User: a human operator of Queenswood, 1:1 with
  a Keycloak `sub`. Kept strictly separate from Party (a customer in
  the banking domain) -- see docs/prd/users.md. Memberships
  (`bank-membership`) join Users to Organizations with a Role."
  (:require
    [com.repldriven.mono.bank-user.core :as core]))

(defn upsert-by-keycloak-sub
  "Idempotent upsert keyed by Keycloak `sub`. First call (unknown
  sub) creates a User from the OIDC claims; subsequent calls apply
  fresh claims (email/name/avatar may have changed) and refresh
  `updated-at`. user-id + status stay put across re-signins.

  Args:
  - txn: FDB transaction or db handle.
  - claims: map with `:keycloak-sub` (required), `:email`, `:name`,
    `:avatar-url`, `:identity-provider` (`:identity-provider-*`
    keyword; defaults to `:identity-provider-unknown`).

  Returns the User map or an anomaly."
  [txn claims]
  (core/upsert-by-keycloak-sub txn claims))

(defn find-by-keycloak-sub
  "Look up a User by Keycloak `sub`. Returns the User map or nil if
  absent (NOT an anomaly -- callers use the nil to drive
  first-signin onboarding).

  Args:
  - txn: FDB transaction or db handle.
  - sub: Keycloak subject claim (string)."
  [txn sub]
  (core/find-by-keycloak-sub txn sub))

(defn find-by-id
  "Load a User by `user-id`. Returns the User map or a
  `:user/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - user-id: user id (string)."
  [txn user-id]
  (core/find-by-id txn user-id))
