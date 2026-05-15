(ns com.repldriven.mono.bank-test-identity-provider.interface
  "Test-only stand-in for `bank-identity-provider`. Selected by the
  Polylith test profile in test bundles; application code never
  depends on this brick directly. The `TestIdentityProviderClient`
  record extends `bank-identity-provider.interface/IdentityProvider`,
  so any code that calls into the production interface — including
  `bank-organization.core` and `bank-api.auth` — dispatches into
  this brick transparently when a test client is wired.

  Beyond the public surface, exposes `mint-token` so the scenario
  harness can synthesise JWTs without a live Keycloak."
  (:require
    com.repldriven.mono.bank-test-identity-provider.system

    [com.repldriven.mono.bank-identity-provider.interface :as idp]
    [com.repldriven.mono.bank-test-identity-provider.store :as store])
  (:import
    (com.repldriven.mono.bank_test_identity_provider.store
     TestIdentityProviderClient)))

(extend-protocol idp/IdentityProvider
 TestIdentityProviderClient
   (-create-service-account [client data] (store/create-client client data))
   (-revoke-service-account [client organization-id]
     (store/delete-client client organization-id))
   (-rotate-secret [client organization-id]
     (store/regenerate-secret client organization-id))
   (-exchange-client-credentials [client creds]
     (store/exchange-client-credentials client creds))
   (-verify-token [client jwt-string opts]
     (store/verify-token client jwt-string opts))
   (-get-jwks [client] (store/jwks client))
   (-get-issuer [client] (store/issuer client)))

(defn mint-token
  "Test-only: sign a JWT with the stub's private key. `claims`
  should include `:azp` (the client/org id) and may include
  `:aud` and `:realm_access`. Production callers should never use
  this — the realm mints tokens via the `/token` endpoint.

  Args:
  - client: test identity-provider client component.
  - claims: claims map to embed in the token."
  [client claims]
  (store/mint-token client claims))
