(ns com.repldriven.mono.bank-identity-provider.core
  "Orchestration: create a service account (Keycloak client + secret
  fetch), revoke, rotate, and verify JWTs minted by the realm."
  (:require
    [com.repldriven.mono.bank-identity-provider.store :as store]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [buddy.core.keys :as buddy-keys]
    [buddy.sign.jws :as jws]
    [buddy.sign.jwt :as jwt]))

(defn create-service-account
  "Create a Keycloak client for `organization-id` and return
  `{:client-id … :client-secret …}`. The client_id is set to the
  organization-id; secret is fetched after creation. Idempotent: a
  pre-existing client with the same id surfaces as
  `:identity-provider/client-already-exists`."
  [client {:keys [organization-id name status]}]
  (let-nom>
    [_ (store/create-client client
                            {:organization-id organization-id
                             :name name
                             :status status})
     result (store/client-secret client organization-id)]
    result))

(defn revoke-service-account
  "Delete the Keycloak client for `organization-id`. Idempotent."
  [client organization-id]
  (store/delete-client client organization-id))

(defn rotate-secret
  "Issue a fresh `client_secret` for `organization-id`. Returns
  `{:client-id … :client-secret …}`."
  [client organization-id]
  (store/regenerate-secret client organization-id))

(defn- find-jwk
  [jwks kid]
  (some #(when (= kid (:kid %)) %) (:keys jwks)))

(defn verify-token
  "Validate a JWT minted by the realm and return its claims map, or
  an `:auth/unauthenticated` rejection.

  Checks: signature against the JWKS, `iss` matches the configured
  realm issuer, `aud` is in `expected-audiences`, `exp` not past.
  On an unknown `kid` the JWKS is force-refreshed once (Keycloak
  rotation case)."
  [client jwt-string {:keys [expected-audiences]}]
  (try
    (let [header (jws/decode-header jwt-string)
          kid (some-> header
                      :kid)
          jwks (store/jwks! client)]
      (if (error/anomaly? jwks)
        jwks
        (let [jwk (or (find-jwk jwks kid)
                      ;; kid not in cache — try a forced refresh once
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

(defn get-jwks
  "Return the realm's JWKS (refreshing if stale)."
  [client]
  (store/jwks! client))

(defn get-issuer
  "Return the configured realm issuer URL."
  [client]
  (store/issuer client))
