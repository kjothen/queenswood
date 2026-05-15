(ns com.repldriven.mono.bank-identity-provider.core
  "The production identity-provider client: a `defrecord` that holds
  the Keycloak admin token + JWKS atoms and implements
  `IdentityProvider` inline by orchestrating calls into `store` (the
  low-level Keycloak REST layer)."
  (:require
    [com.repldriven.mono.bank-identity-provider.protocol :as protocol]
    [com.repldriven.mono.bank-identity-provider.store :as store]

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
          jwks (store/jwks! client)]
      (if (error/anomaly? jwks)
        jwks
        (let [jwk (or (find-jwk jwks kid)
                      ;; kid not in cache — force-refresh once in case
                      ;; Keycloak rotated.
                      (find-jwk (store/jwks! client true) kid))]
          (if-not jwk
            (error/reject :auth/unauthenticated
                          {:message "Token signing key not recognised"
                           :kid kid})
            (let [public-key (buddy-keys/jwk->public-key jwk)
                  claims (jwt/unsign jwt-string
                                     public-key
                                     {:alg :rs256
                                      :iss (store/issuer client)})]
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

(defrecord IdentityProviderClient [config admin-token jwks]
  store/Client
    (-config [_] config)
    (-admin-token-atom [_] admin-token)
    (-jwks-atom [_] jwks)
  protocol/IdentityProvider
    (-create-service-account [this {:keys [organization-id name status]}]
      (let-nom> [_ (store/create-client this
                                        {:organization-id organization-id
                                         :name name
                                         :status status})
                 result (store/client-secret this organization-id)]
        result))
    (-revoke-service-account [this organization-id]
      (store/delete-client this organization-id))
    (-rotate-secret [this organization-id]
      (store/regenerate-secret this organization-id))
    (-exchange-client-credentials [this creds]
      (store/exchange-client-credentials this creds))
    (-verify-token [this jwt-string opts]
      (verify-token-impl this jwt-string opts))
    (-get-jwks [this] (store/jwks! this))
    (-get-issuer [this] (store/issuer this)))

(defn ->client
  "Build an IdentityProviderClient. `config` carries
  `:base-url`, `:realm`, `:admin-client-id`, `:admin-client-secret`."
  [config]
  (->IdentityProviderClient config (atom nil) (atom nil)))
