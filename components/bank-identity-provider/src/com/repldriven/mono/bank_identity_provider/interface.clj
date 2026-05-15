(ns com.repldriven.mono.bank-identity-provider.interface
  "Per-org service-account provisioning and JWT verification, backed
  by a central Keycloak realm. Replaces the legacy `bank-api-key`
  brick. Service accounts are Keycloak clients with `client_credentials`
  grant enabled; their `clientId` equals the Queenswood
  `organization-id` so JWT `azp` maps directly back to the tenant.
  Issued tokens carry an `aud` claim of `queenswood-api-test` or
  `queenswood-api-live` depending on org status."
  (:require
    com.repldriven.mono.bank-identity-provider.system

    [com.repldriven.mono.bank-identity-provider.core :as core]))

(defn create-service-account
  "Create a Keycloak service-account client for an organization.
  Returns `{:client-id … :client-secret …}` (the secret is only
  available at creation time) or an anomaly.

  Args:
  - client: identity-provider client component.
  - data: map with `:organization-id`, optional `:name`, and
    `:status` (`:organization-status-test` or
    `:organization-status-live`); status selects the per-env
    audience claim."
  [client data]
  (core/create-service-account client data))

(defn revoke-service-account
  "Delete the Keycloak service-account client for `organization-id`.
  Idempotent.

  Args:
  - client: identity-provider client component.
  - organization-id: owning organization id."
  [client organization-id]
  (core/revoke-service-account client organization-id))

(defn rotate-secret
  "Rotate the `client_secret` for `organization-id`. Returns
  `{:client-id … :client-secret …}` or an anomaly.

  Args:
  - client: identity-provider client component.
  - organization-id: owning organization id."
  [client organization-id]
  (core/rotate-secret client organization-id))

(defn verify-token
  "Validate a JWT against the realm's JWKS. Returns the claims map
  or an `:auth/unauthenticated` rejection.

  Args:
  - client: identity-provider client component.
  - jwt-string: the raw `Bearer` token value.
  - opts: map with `:expected-audiences` (set of strings; token's
    `aud` must intersect)."
  [client jwt-string opts]
  (core/verify-token client jwt-string opts))

(defn get-jwks
  "Return the realm's JWKS (refreshing if stale).

  Args:
  - client: identity-provider client component."
  [client]
  (core/get-jwks client))

(defn get-issuer
  "Return the realm's issuer URL (`iss` claim value).

  Args:
  - client: identity-provider client component."
  [client]
  (core/get-issuer client))
