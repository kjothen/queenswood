(ns com.repldriven.mono.keycloak.system.components
  (:require
    [com.repldriven.mono.keycloak.identity-provider :as kc-idp]
    [com.repldriven.mono.system.interface :as system]))

(def identity-provider
  "Keycloak-backed `IdentityProvider`. Talks to a live Keycloak realm
  over HTTP — pair `:base-url` with whatever exposes the realm (a
  testcontainer for tests, an in-cluster Service URL for prod).
  Config:
  - `:base-url` — Keycloak base URL (no trailing `/realms/…`).
  - `:realm` — realm name.
  - `:admin-client-id` / `:admin-client-secret` — credentials for
    the service-account client this brick uses to call Admin REST."
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (kc-idp/->client config)))
   :system/config {:base-url system/required-component
                   :realm system/required-component
                   :admin-client-id system/required-component
                   :admin-client-secret system/required-component}
   :system/instance-schema some?})
