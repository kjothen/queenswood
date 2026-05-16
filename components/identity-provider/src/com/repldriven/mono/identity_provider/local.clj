(ns com.repldriven.mono.identity-provider.local
  "In-memory implementation of `IdentityProvider`. Generates an RSA
  keypair on construction so the stub can both mint and verify JWTs
  without talking to any external service. Suitable for fast brick
  tests; high-fidelity tests should use the `keycloak` adapter
  pointed at a real Keycloak (testcontainer or otherwise).

  Audience handling is domain-agnostic: callers pass a `:status`
  keyword on `create-service-account`, the record looks up the
  matching audience string in its configured `audiences-by-status`
  map, and stamps it on subsequently-issued tokens for that client."
  (:require
    [com.repldriven.mono.identity-provider.protocol :as protocol]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as util]

    [buddy.sign.jwt :as jwt])
  (:import
    (java.security KeyPair KeyPairGenerator)
    (java.util Base64)))

(defn- generate-rsa-keypair
  []
  (let [gen (KeyPairGenerator/getInstance "RSA")]
    (.initialize gen 2048)
    (.generateKeyPair gen)))

(defn- b64url
  ^String [^bytes bs]
  (.encodeToString (Base64/getUrlEncoder) bs))

(defn- public-jwk
  [^KeyPair kp kid]
  (let [pub (.getPublic kp)
        modulus (.getModulus pub)
        exponent (.getPublicExponent pub)]
    {:kty "RSA"
     :kid kid
     :alg "RS256"
     :use "sig"
     :n (b64url (.toByteArray modulus))
     :e (b64url (.toByteArray exponent))}))

(defprotocol Stub
  "Internal accessors so the implementation fns below can pull the
  keypair / state off the defrecord without leaking field shape."
  (-keypair [_])
  (-kid [_])
  (-issuer [_])
  (-state [_])
  (-audiences-by-status [_]))

(defn- mint-token*
  [client claims]
  (let [now-s (long (/ (util/now) 1000))
        full-claims (merge {:iss (-issuer client)
                            :iat now-s
                            :exp (+ now-s 3600)
                            :jti (str "local-jti-" (util/uuidv7))}
                           claims)]
    (jwt/sign full-claims
              (.getPrivate ^KeyPair (-keypair client))
              {:alg :rs256
               :header {:kid (-kid client) :alg "RS256" :typ "JWT"}})))

(defn- create-client-impl
  [client {:keys [organization-id status]}]
  (let [secret (str "local-secret-" (util/uuidv7))
        audience (get (-audiences-by-status client) status)]
    (swap! (-state client) assoc-in
      [:clients organization-id]
      {:client-id organization-id
       :client-secret secret
       :audience audience})
    {:client-id organization-id :client-secret secret}))

(defn- delete-client-impl
  [client client-id]
  (swap! (-state client) update :clients dissoc client-id)
  {:client-id client-id})

(defn- regenerate-secret-impl
  [client client-id]
  (let [secret (str "local-secret-" (util/uuidv7))]
    (swap! (-state client) assoc-in [:clients client-id :client-secret] secret)
    {:client-id client-id :client-secret secret}))

(defn- exchange-client-credentials-impl
  [client {:keys [client-id client-secret]}]
  (let [registered (get-in @(-state client) [:clients client-id])]
    (if (or (nil? registered) (not= client-secret (:client-secret registered)))
      (error/reject :auth/invalid-client
                    {:message "Unknown client_id or client_secret mismatch"})
      (let [aud (:audience registered)
            token (mint-token* client
                               {:azp client-id
                                :sub client-id
                                :aud [aud]
                                :realm_access {:roles ["org"]}})]
        {:access_token token
         :expires_in 3600
         :token_type "Bearer"
         :scope aud}))))

(defn- verify-token-impl
  [client jwt-string {:keys [expected-audiences]}]
  (try
    (let [public-key (.getPublic ^KeyPair (-keypair client))
          claims (jwt/unsign jwt-string
                             public-key
                             {:alg :rs256 :iss (-issuer client)})]
      (if (and (seq expected-audiences)
               (not (some expected-audiences
                          (cond-> (:aud claims)
                                  (string? (:aud claims))
                                  vector))))
        (error/reject :auth/unauthenticated
                      {:message "Token audience not accepted"
                       :aud (:aud claims)})
        claims))
    (catch Exception e
      (error/reject :auth/unauthenticated
                    {:message (str "Token verification failed: "
                                   (.getMessage e))}))))

(defn- jwks-impl
  [client]
  {:keys [(public-jwk (-keypair client) (-kid client))]
   :fetched-at (util/now)})

(defrecord LocalIdentityProvider [keypair kid issuer state audiences-by-status]
  Stub
    (-keypair [_] keypair)
    (-kid [_] kid)
    (-issuer [_] issuer)
    (-state [_] state)
    (-audiences-by-status [_] audiences-by-status)
  protocol/IdentityProvider
    (-create-service-account [this data] (create-client-impl this data))
    (-revoke-service-account [this organization-id]
      (delete-client-impl this organization-id))
    (-rotate-secret [this organization-id]
      (regenerate-secret-impl this organization-id))
    (-exchange-client-credentials [this creds]
      (exchange-client-credentials-impl this creds))
    (-verify-token [this jwt-string opts]
      (verify-token-impl this jwt-string opts))
    (-get-jwks [this] (jwks-impl this))
    (-get-issuer [_] issuer))

(defn ->client
  "Build a `LocalIdentityProvider`. `config` may carry `:issuer`
  (default `https://local.invalid/`) and must carry
  `:audiences-by-status` — a map from organization-status keyword to
  audience string."
  [{:keys [issuer audiences-by-status]
    :or {issuer "https://local.invalid/"}}]
  (->LocalIdentityProvider
   (generate-rsa-keypair)
   (str "local-key-" (util/uuidv7))
   issuer
   (atom {:clients {}})
   (or audiences-by-status {})))
