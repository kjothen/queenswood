(ns com.repldriven.mono.bank-test-identity-provider.interface
  "Test-only stand-in for `bank-identity-provider`. Selected by the
  Polylith test profile in test bundles; application code never
  depends on this brick directly. The `TestIdentityProviderClient`
  record in `store` implements `bank-identity-provider.interface/
  IdentityProvider` inline, so any code that calls the production
  interface dispatches here transparently when a test client is
  wired.

  Beyond the public surface, exposes `mint-token` so the scenario
  harness can synthesise JWTs without a live Keycloak."
  (:require
    com.repldriven.mono.bank-test-identity-provider.system

    [com.repldriven.mono.bank-test-identity-provider.store :as store]))

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
