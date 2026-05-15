(ns com.repldriven.mono.bank-identity-provider.store
  "Keycloak Admin REST + token endpoint client. Holds the cached
  admin access token and JWKS in atoms on the client record. All
  HTTP goes through `http-client` so failures surface as
  `:http-client/request` or `:identity-provider/*` anomalies."
  (:require
    [com.repldriven.mono.bank-identity-provider.domain :as domain]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.utility.interface :as util]))

(def ^:private jwks-ttl-ms (* 10 60 1000))

(defprotocol Client
  (-config [_])
  (-admin-token-atom [_])
  (-jwks-atom [_]))

(defrecord IdentityProviderClient [config admin-token jwks]
  Client
    (-config [_] config)
    (-admin-token-atom [_] admin-token)
    (-jwks-atom [_] jwks))

(defn ->client
  "Build an IdentityProviderClient. `config` carries
  `:base-url`, `:realm`, `:admin-client-id`, `:admin-client-secret`."
  [config]
  (->IdentityProviderClient config (atom nil) (atom nil)))

(defn- realm-url
  [{:keys [base-url realm]} & path-parts]
  (apply str base-url "/realms/" realm path-parts))

(defn- admin-url
  [{:keys [base-url realm]} & path-parts]
  (apply str base-url "/admin/realms/" realm path-parts))

(defn- fetch-admin-token
  [config]
  (let-nom>
    [res (http/request
          {:method :post
           :url (realm-url config "/protocol/openid-connect/token")
           :headers {"content-type" "application/x-www-form-urlencoded"}
           :body (str "grant_type=client_credentials"
                      "&client_id=" (:admin-client-id config)
                      "&client_secret=" (:admin-client-secret config))})
     body (http/res->edn res)]
    (or (some-> (domain/parse-token-response body)
                (assoc :fetched-at (util/now)))
        (error/fail :identity-provider/admin-token-malformed
                    {:message
                     "Keycloak admin token response missing access_token"}))))

(defn- admin-token!
  "Return a valid admin access token, refreshing if expired."
  [client]
  (let [config (-config client)
        a (-admin-token-atom client)
        cached @a]
    (if-not (domain/admin-token-expired? cached (util/now))
      (:access-token cached)
      (let [fresh (fetch-admin-token config)]
        (if (error/anomaly? fresh)
          fresh
          (do (reset! a fresh) (:access-token fresh)))))))

(defn- admin-headers
  [token]
  {"authorization" (str "Bearer " token)
   "content-type" "application/json"})

(defn create-client
  "Create a Keycloak client (per-org service account). Returns the
  created client representation or an anomaly. The Keycloak Create
  Client endpoint returns 201 with the new client's URL in the
  `Location` header — we then fetch the client to get its UUID and
  pair it with a secret."
  [client {:keys [organization-id name status]}]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       _ (http/request
          {:method :post
           :url (admin-url config "/clients")
           :headers (admin-headers token)
           :body (json/write-str
                  (domain/new-client-representation
                   {:organization-id organization-id
                    :name name
                    :status status}))})]
      {:client-id organization-id})))

(defn client-secret
  "Fetch the current client_secret for a given Keycloak clientId. The
  Admin API requires the Keycloak UUID, not the clientId — we look it
  up first."
  [client client-id]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       uuid (some-> clients
                    first
                    :id)
       _ (when-not uuid
           (error/reject :identity-provider/client-not-found
                         {:message "No Keycloak client matches client-id"
                          :client-id client-id}))
       sec-res (http/request
                {:method :get
                 :url (admin-url config "/clients/" uuid "/client-secret")
                 :headers (admin-headers token)})
       sec (http/res->edn sec-res)]
      {:client-id client-id :client-secret (:value sec)})))

(defn delete-client
  "Delete the Keycloak client matching `client-id`. Idempotent: a
  404 is treated as success (already gone)."
  [client client-id]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       uuid (some-> clients
                    first
                    :id)]
      (if uuid
        (let-nom>
          [_ (http/request
              {:method :delete
               :url (admin-url config "/clients/" uuid)
               :headers (admin-headers token)})]
          {:client-id client-id})
        {:client-id client-id}))))

(defn regenerate-secret
  "Rotate the client_secret for a given Keycloak clientId."
  [client client-id]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       uuid (some-> clients
                    first
                    :id)
       _ (when-not uuid
           (error/reject :identity-provider/client-not-found
                         {:message "No Keycloak client matches client-id"
                          :client-id client-id}))
       res (http/request
            {:method :post
             :url (admin-url config "/clients/" uuid "/client-secret")
             :headers (admin-headers token)})
       sec (http/res->edn res)]
      {:client-id client-id :client-secret (:value sec)})))

(defn- fetch-jwks
  [config]
  (let-nom>
    [res (http/request {:method :get
                        :url (realm-url config
                                        "/protocol/openid-connect/certs")})
     body (http/res->edn res)]
    (or (some-> (domain/parse-jwks body)
                (assoc :fetched-at (util/now)))
        (error/fail :identity-provider/jwks-malformed
                    {:message "Keycloak JWKS response missing :keys"}))))

(defn jwks!
  "Return cached JWKS, refreshing if stale. If `force-refresh?` is
  truthy, bypass the cache (used when a `kid` is unknown)."
  ([client] (jwks! client false))
  ([client force-refresh?]
   (let [config (-config client)
         a (-jwks-atom client)
         cached @a]
     (if (and (not force-refresh?)
              (not (domain/jwks-stale? cached (util/now) jwks-ttl-ms)))
       cached
       (let [fresh (fetch-jwks config)]
         (if (error/anomaly? fresh)
           fresh
           (do (reset! a fresh) fresh)))))))

(defn issuer
  "Configured issuer URL for the realm."
  [client]
  (realm-url (-config client)))
