(ns com.repldriven.mono.bank-identity-provider.interface
  "Per-org service-account provisioning and JWT verification, backed
  by a central Keycloak realm. Replaces the legacy `bank-api-key`
  brick. Service accounts are Keycloak clients with `client_credentials`
  grant enabled; their `clientId` equals the Queenswood
  `organization-id` so JWT `azp` maps directly back to the tenant.
  Issued tokens carry an `aud` claim of `queenswood-api-test` or
  `queenswood-api-live` depending on org status.

  Both the production `IdentityProviderClient` (in this brick) and
  the test `TestIdentityProviderClient` (in `bank-test-identity-
  provider`) implement the `IdentityProvider` protocol re-exported
  here, so callers depend on the protocol — not on either store —
  and the system YAML picks which one is wired."
  (:require
    com.repldriven.mono.bank-identity-provider.system

    [com.repldriven.mono.bank-identity-provider.protocol :as protocol]))

;; Re-export so external implementers can pull the protocol off the
;; interface namespace without dipping into internals — same pattern
;; as `message-bus.interface` does for Producer/Consumer.
(def IdentityProvider protocol/IdentityProvider)

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
  (protocol/-create-service-account client data))

(defn revoke-service-account
  "Delete the Keycloak service-account client for `organization-id`.
  Idempotent.

  Args:
  - client: identity-provider client component.
  - organization-id: owning organization id."
  [client organization-id]
  (protocol/-revoke-service-account client organization-id))

(defn rotate-secret
  "Rotate the `client_secret` for `organization-id`. Returns
  `{:client-id … :client-secret …}` or an anomaly.

  Args:
  - client: identity-provider client component.
  - organization-id: owning organization id."
  [client organization-id]
  (protocol/-rotate-secret client organization-id))

(defn exchange-client-credentials
  "Run the OAuth2 `client_credentials` flow against the realm.
  Returns the raw token response with snake-case keys
  (`:access_token`, `:expires_in`, `:token_type`, `:scope`) so the
  caller can pass it through to an OAuth2 client without rekeying,
  or an anomaly.

  Args:
  - client: identity-provider client component.
  - creds: map with `:client-id`, `:client-secret`, and optional
    `:scope`."
  [client creds]
  (protocol/-exchange-client-credentials client creds))

(defn verify-token
  "Validate a JWT against the realm's JWKS. Returns the claims map
  or an `:auth/unauthenticated` rejection.

  Args:
  - client: identity-provider client component.
  - jwt-string: the raw `Bearer` token value.
  - opts: map with `:expected-audiences` (set of strings; token's
    `aud` must intersect)."
  [client jwt-string opts]
  (protocol/-verify-token client jwt-string opts))

(defn get-jwks
  "Return the realm's JWKS (refreshing if stale).

  Args:
  - client: identity-provider client component."
  [client]
  (protocol/-get-jwks client))

(defn get-issuer
  "Return the realm's issuer URL (`iss` claim value).

  Args:
  - client: identity-provider client component."
  [client]
  (protocol/-get-issuer client))
