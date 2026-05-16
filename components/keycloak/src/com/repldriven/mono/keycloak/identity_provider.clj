(ns com.repldriven.mono.keycloak.identity-provider
  "`KeycloakIdentityProvider` — a defrecord that holds the Keycloak
  admin-token + JWKS atoms plus the realm config, and implements the
  `identity-provider` brick's `IdentityProvider` protocol inline by
  orchestrating calls into this brick's `core` namespace.

  Audience handling is domain-agnostic: callers pass an `:audience`
  string on `create-service-account` and the adapter attaches it as
  the client-scope name on the new client's `defaultClientScopes`.
  The realm import owns the actual audience mapper for that scope, so
  tokens minted by the client carry the right `aud` claim
  automatically."
  (:require
    [com.repldriven.mono.identity-provider.interface :as identity-provider]
    [com.repldriven.mono.keycloak.core :as core]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [buddy.core.keys :as buddy-keys]
    [buddy.sign.jws :as jws]
    [buddy.sign.jwt :as jwt]))

(defn- find-jwk
  [jwks kid]
  (some #(when (= kid (:kid %)) %) (:keys jwks)))

(defn- verify-token-impl
  [client jwt-string {:keys [expected-audiences]}]
  (try
    (let [header (jws/decode-header jwt-string)
          kid (some-> header
                      :kid)
          jwks (core/jwks! client)]
      (if (error/anomaly? jwks)
        jwks
        (let [jwk (or (find-jwk jwks kid)
                      ;; kid not in cache — force-refresh once in case
                      ;; Keycloak rotated.
                      (find-jwk (core/jwks! client true) kid))]
          (if-not jwk
            (error/reject :auth/unauthenticated
                          {:message "Token signing key not recognised"
                           :kid kid})
            (let [public-key (buddy-keys/jwk->public-key jwk)
                  claims (jwt/unsign jwt-string
                                     public-key
                                     {:alg :rs256
                                      :iss (core/issuer client)})]
              (if (and (seq expected-audiences)
                       (not (some expected-audiences
                                  (cond-> (:aud claims)
                                          (string? (:aud claims))
                                          vector))))
                (error/reject :auth/unauthenticated
                              {:message "Token audience not accepted"
                               :aud (:aud claims)})
                claims))))))
    (catch Exception e
      (error/reject :auth/unauthenticated
                    {:message (str "Token verification failed: "
                                   (.getMessage e))}))))

(defrecord KeycloakIdentityProvider [config admin-token jwks]
  core/Client
    (-config [_] config)
    (-admin-token-atom [_] admin-token)
    (-jwks-atom [_] jwks)
  identity-provider/IdentityProvider
    (-create-service-account [this {:keys [organization-id name audience]}]
      (let-nom> [_ (core/create-client this
                                       {:organization-id organization-id
                                        :name name
                                        :audience audience})
                 result (core/client-secret this organization-id)]
        result))
    (-revoke-service-account [this organization-id]
      (core/delete-client this organization-id))
    (-rotate-secret [this organization-id]
      (core/regenerate-secret this organization-id))
    (-exchange-client-credentials [this creds]
      (core/exchange-client-credentials this creds))
    (-verify-token [this jwt-string opts]
      (verify-token-impl this jwt-string opts))
    (-get-jwks [this] (core/jwks! this))
    (-get-issuer [this] (core/issuer this)))

(defn ->client
  "Build a `KeycloakIdentityProvider`. `config` carries `:base-url`,
  `:realm`, `:admin-client-id`, `:admin-client-secret`."
  [{:keys [base-url realm admin-client-id admin-client-secret]}]
  (->KeycloakIdentityProvider
   {:base-url base-url
    :realm realm
    :admin-client-id admin-client-id
    :admin-client-secret admin-client-secret}
   (atom nil)
   (atom nil)))
