(ns com.repldriven.mono.bank-test-identity-provider.interface
  "Test-only stand-in for `bank-identity-provider`. Implements the
  same public surface (`create-service-account`, `verify-token`,
  `get-jwks`, `get-issuer`, `revoke-service-account`,
  `rotate-secret`) backed by an in-memory RSA keypair, plus
  `mint-token` so scenario runners can synthesise JWTs without
  needing a live Keycloak. Selected via Integrant profile in test
  bundles; application code never depends on this brick directly."
  (:require
    com.repldriven.mono.bank-test-identity-provider.system

    [com.repldriven.mono.bank-test-identity-provider.store :as store]))

(defn create-service-account
  "Register a stub service-account client. Returns
  `{:client-id … :client-secret …}`. Mirrors the real brick.

  Args:
  - client: test identity-provider client component.
  - data: map with `:organization-id` and (optional) `:status`."
  [client data]
  (store/create-client client data))

(defn revoke-service-account
  "Drop a stub client. Mirrors the real brick.

  Args:
  - client: test identity-provider client component.
  - organization-id: id to remove."
  [client organization-id]
  (store/delete-client client organization-id))

(defn rotate-secret
  "Issue a fresh stub secret. Mirrors the real brick.

  Args:
  - client: test identity-provider client component.
  - organization-id: id whose secret to rotate."
  [client organization-id]
  (store/regenerate-secret client organization-id))

(defn verify-token
  "Validate a JWT minted by `mint-token`. Mirrors the real brick.

  Args:
  - client: test identity-provider client component.
  - jwt-string: the raw token value.
  - opts: map with `:expected-audiences`."
  [client jwt-string opts]
  (store/verify-token client jwt-string opts))

(defn get-jwks
  "Return the stub's JWKS (one key, fresh per process).

  Args:
  - client: test identity-provider client component."
  [client]
  (store/jwks client))

(defn get-issuer
  "Return the stub's issuer URL (`https://test.invalid/...`).

  Args:
  - client: test identity-provider client component."
  [client]
  (store/issuer client))

(defn mint-token
  "Test-only: sign a JWT with the stub's private key. `claims`
  should include `:azp` (the client/org id) and may include
  `:aud` and `:realm_access`.

  Args:
  - client: test identity-provider client component.
  - claims: claims map to embed in the token."
  [client claims]
  (store/mint-token client claims))
