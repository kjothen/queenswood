(ns com.repldriven.mono.bank-identity-provider.protocol)

;; The contract every identity-provider client implements. The
;; production `IdentityProviderClient` (talks to Keycloak) and the
;; test `TestIdentityProviderClient` (in-memory) both extend this
;; inline at their defrecord, so callers depend on the protocol —
;; not on either store — and the monolith / test bundles wire
;; whichever client suits their environment.
(defprotocol IdentityProvider
  (-create-service-account [this data])
  (-revoke-service-account [this organization-id])
  (-rotate-secret [this organization-id])
  (-exchange-client-credentials [this creds])
  (-verify-token [this jwt-string opts])
  (-get-jwks [this])
  (-get-issuer [this]))
