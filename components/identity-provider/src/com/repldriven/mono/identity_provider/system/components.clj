(ns com.repldriven.mono.identity-provider.system.components
  (:require
    [com.repldriven.mono.identity-provider.local :as local]
    [com.repldriven.mono.system.interface :as system]))

(def local-client
  "In-memory `LocalIdentityProvider` — fast, no external dependencies.
  Config:
  - `:issuer` — string baked into the JWT `iss` claim (and verified
    on roundtrip). Default `https://local.invalid/`.
  - `:audiences-by-status` — required map from organization-status
    keyword to audience string. Tokens issued for a client carry the
    audience matching the status passed at create-time."
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (local/->client config)))
   :system/config {:issuer "https://local.invalid/"
                   :audiences-by-status system/required-component}
   :system/instance-schema some?})
