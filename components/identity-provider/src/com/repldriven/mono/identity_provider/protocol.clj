(ns com.repldriven.mono.identity-provider.protocol)

;; Substrate protocol every identity-provider implementation extends.
;; The bundled `LocalIdentityProvider` (in this brick) and the
;; `KeycloakIdentityProvider` (in the `keycloak` brick) both implement
;; this inline at their defrecord — the same pattern message-bus uses
;; for Producer/Consumer with LocalProducer/PulsarProducer.
(defprotocol IdentityProvider
  (-create-service-account [this data])
  (-revoke-service-account [this organization-id])
  (-rotate-secret [this organization-id])
  (-exchange-client-credentials [this creds])
  (-verify-token [this jwt-string opts])
  (-get-jwks [this])
  (-get-issuer [this]))
