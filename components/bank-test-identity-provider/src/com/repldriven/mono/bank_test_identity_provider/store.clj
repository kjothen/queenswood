(ns com.repldriven.mono.bank-test-identity-provider.store
  "In-memory stand-in for `bank-identity-provider/store`. Generates
  an RSA keypair on construction so the stub can both mint and
  verify JWTs without talking to a real Keycloak."
  (:require
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

(defprotocol Client
  (-keypair [_])
  (-kid [_])
  (-issuer [_])
  (-state [_]))

(defrecord TestIdentityProviderClient [keypair kid issuer state]
  Client
    (-keypair [_] keypair)
    (-kid [_] kid)
    (-issuer [_] issuer)
    (-state [_] state))

(defn ->client
  "Build a stub IDP client. `config` may carry `:issuer`; defaults
  to `https://test.invalid/realms/queenswood`."
  [{:keys [issuer] :or {issuer "https://test.invalid/realms/queenswood"}}]
  (->TestIdentityProviderClient
   (generate-rsa-keypair)
   (str "test-key-" (util/uuidv7))
   issuer
   (atom {:clients {}})))

(defn create-client
  "Register a stub client. Returns `{:client-id … :client-secret …}`."
  [client {:keys [organization-id]}]
  (let [secret (str "test-secret-" (util/uuidv7))]
    (swap! (-state client) assoc-in
      [:clients organization-id]
      {:client-id organization-id
       :client-secret secret})
    {:client-id organization-id :client-secret secret}))

(defn delete-client
  [client client-id]
  (swap! (-state client) update :clients dissoc client-id)
  {:client-id client-id})

(defn regenerate-secret
  [client client-id]
  (let [secret (str "test-secret-" (util/uuidv7))]
    (swap! (-state client) assoc-in [:clients client-id :client-secret] secret)
    {:client-id client-id :client-secret secret}))

(defn jwks
  [client]
  {:keys [(public-jwk (-keypair client) (-kid client))]
   :fetched-at (util/now)})

(defn issuer
  [client]
  (-issuer client))

(defn mint-token
  "Sign a JWT for tests. `claims` should include `:azp` (the
  client-id), and may include `:aud`, `:realm_access`, and a
  custom `:exp` override."
  [client claims]
  (let [now-s (long (/ (util/now) 1000))
        full-claims (merge {:iss (-issuer client)
                            :iat now-s
                            :exp (+ now-s 3600)
                            :jti (str "test-jti-" (util/uuidv7))}
                           claims)]
    (jwt/sign full-claims
              (.getPrivate ^KeyPair (-keypair client))
              {:alg :rs256
               :header {:kid (-kid client) :alg "RS256" :typ "JWT"}})))

(defn- scope->audience
  "Map an OAuth2 `scope` value back to the audience claim it grants.
  Mirrors the production realm import, which provisions one client
  scope per env. Unknown scopes get the test audience."
  [scope]
  (case scope
    "queenswood-api-live" "queenswood-api-live"
    "queenswood-api-test"))

(defn exchange-client-credentials
  "Validate a `client_credentials` request against the in-memory
  client registry and mint a fresh JWT. Returns the OAuth2 token
  response with snake-case keys mirroring Keycloak's contract, or
  an `:auth/invalid-client` rejection."
  [client {:keys [client-id client-secret scope]}]
  (let [registered (get-in @(-state client) [:clients client-id])
        aud (scope->audience scope)]
    (if (or (nil? registered) (not= client-secret (:client-secret registered)))
      (error/reject :auth/invalid-client
                    {:message "Unknown client_id or client_secret mismatch"})
      (let [token (mint-token client
                              {:azp client-id
                               :sub client-id
                               :aud [aud]
                               :realm_access {:roles ["org"]}})]
        {:access_token token
         :expires_in 3600
         :token_type "Bearer"
         :scope (or scope aud)}))))

(defn verify-token
  "Verify a JWT minted by `mint-token`. Returns claims or an
  `:auth/unauthenticated` rejection."
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
