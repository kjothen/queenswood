(ns com.repldriven.mono.bank-identity-provider.interface
  "Per-org service-account provisioning and JWT verification, backed
  by a central Keycloak realm. Replaces the legacy `bank-api-key`
  brick. Service accounts are Keycloak clients with `client_credentials`
  grant enabled; their `clientId` equals the Queenswood
  `organization-id` so JWT `azp` maps directly back to the tenant.
  Issued tokens carry an `aud` claim of `queenswood-api-test` or
  `queenswood-api-live` depending on org status.

  Public surface is dispatched through the `IdentityProvider` protocol
  so `bank-test-identity-provider` can plug in an in-memory stand-in
  without code outside this brick caring which one is wired."
  (:require
    com.repldriven.mono.bank-identity-provider.system

    [com.repldriven.mono.bank-identity-provider.core :as core])
  (:import
    (com.repldriven.mono.bank_identity_provider.store
     IdentityProviderClient)))

(defprotocol IdentityProvider
  "Dispatch surface for identity-provider clients. The production
  `IdentityProviderClient` and the test `TestIdentityProviderClient`
  both extend this so production code can call into either without
  knowing the difference."
  (-create-service-account [this data])
  (-revoke-service-account [this organization-id])
  (-rotate-secret [this organization-id])
  (-exchange-client-credentials [this creds])
  (-verify-token [this jwt-string opts])
  (-get-jwks [this])
  (-get-issuer [this]))

(extend-protocol IdentityProvider
 IdentityProviderClient
   (-create-service-account [client data]
     (core/create-service-account client data))
   (-revoke-service-account [client organization-id]
     (core/revoke-service-account client organization-id))
   (-rotate-secret [client organization-id]
     (core/rotate-secret client organization-id))
   (-exchange-client-credentials [client creds]
     (core/exchange-client-credentials client creds))
   (-verify-token [client jwt-string opts]
     (core/verify-token client jwt-string opts))
   (-get-jwks [client] (core/get-jwks client))
   (-get-issuer [client] (core/get-issuer client)))

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
  (-create-service-account client data))

(defn revoke-service-account
  "Delete the Keycloak service-account client for `organization-id`.
  Idempotent.

  Args:
  - client: identity-provider client component.
  - organization-id: owning organization id."
  [client organization-id]
  (-revoke-service-account client organization-id))

(defn rotate-secret
  "Rotate the `client_secret` for `organization-id`. Returns
  `{:client-id … :client-secret …}` or an anomaly.

  Args:
  - client: identity-provider client component.
  - organization-id: owning organization id."
  [client organization-id]
  (-rotate-secret client organization-id))

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
  (-exchange-client-credentials client creds))

(defn verify-token
  "Validate a JWT against the realm's JWKS. Returns the claims map
  or an `:auth/unauthenticated` rejection.

  Args:
  - client: identity-provider client component.
  - jwt-string: the raw `Bearer` token value.
  - opts: map with `:expected-audiences` (set of strings; token's
    `aud` must intersect)."
  [client jwt-string opts]
  (-verify-token client jwt-string opts))

(defn get-jwks
  "Return the realm's JWKS (refreshing if stale).

  Args:
  - client: identity-provider client component."
  [client]
  (-get-jwks client))

(defn get-issuer
  "Return the realm's issuer URL (`iss` claim value).

  Args:
  - client: identity-provider client component."
  [client]
  (-get-issuer client))
