(ns com.repldriven.mono.bank-api.auth
  "Two-path authentication: a stop-gap env-var admin bearer (kept
  until human-auth lands) and a Keycloak-issued JWT for tenant
  service accounts. JWT validation delegates to
  `bank-identity-provider` so JWKS caching, kid rotation and audience
  enforcement live in one place."
  (:require
    [com.repldriven.mono.bank-identity-provider.interface
     :as identity-provider]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.utility.interface :as util]

    [sieppari.context :as sc]

    [clojure.set :as set]
    [clojure.string :as str]))

(defn- extract-bearer
  [request]
  (some-> (get-in request [:headers "authorization"])
          (str/split #" " 2)
          (as-> parts (when (= "Bearer" (first parts)) (second parts)))))

(defn- admin?
  [token admin-api-key]
  (and admin-api-key
       (encryption/bytes-equals? (util/str->bytes token)
                                 (util/str->bytes admin-api-key))))

(defn- admin-auth
  [request]
  ;; TODO(human-auth-PR): replace the env-var admin key with a
  ;; Keycloak admin realm-role. The role is encoded as :admin in
  ;; the JWT's `realm_access.roles` claim once that PR lands.
  {:principal-type :admin
   :principal-id "admin"
   :organization-id (:internal-organization-id request)
   :roles #{:admin :org}})

(defn- service-auth
  "Map a verified JWT claims map to the request auth context. The
  client_id (== organization-id by convention) appears as `:azp`."
  [claims]
  {:principal-type :service
   :principal-id (:azp claims)
   :organization-id (:azp claims)
   :roles (-> claims
              (get-in [:realm_access :roles])
              (->> (map keyword))
              (->> (into #{:org})))
   :token-jti (:jti claims)})

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  token (extract-bearer request)
                  {:keys [admin-api-key identity-provider expected-audiences]}
                  request]
              (cond
               (nil? token)
               ctx

               (admin? token admin-api-key)
               (assoc-in ctx [:request :auth] (admin-auth request))

               :else
               (let [claims (identity-provider/verify-token
                             identity-provider
                             token
                             {:expected-audiences (set expected-audiences)})]
                 (if (map? claims)
                   (assoc-in ctx [:request :auth] (service-auth claims))
                   ctx)))))})

(defn- required-roles
  "Derive the role set a route requires from its OpenAPI metadata.
  `bearerAuth` alone permits any authenticated principal; an
  `x-required-roles` extension narrows it (e.g. `[admin]`)."
  [security]
  (let [schemes (into #{} (mapcat keys) security)
        explicit (->> security
                      (mapcat vals)
                      (mapcat identity)
                      (into #{}))]
    (cond
     (empty? schemes)
     nil
     (seq explicit)
     (into #{} (map keyword) explicit)
     :else
     #{:org})))

(def authorize
  {:name ::authorize
   :enter (fn [ctx]
            (let [request (:request ctx)
                  security
                  (get-in request [:reitit.core/match :data :openapi :security])
                  required (required-roles security)]
              (if (nil? required)
                ctx
                (let [roles (get-in request [:auth :roles] #{})]
                  (cond
                   (empty? roles)
                   (sc/terminate ctx
                                 {:status 401
                                  :headers {"content-type" "application/json"}
                                  :body {:title "UNAUTHORIZED"
                                         :type "auth/unauthenticated"
                                         :status 401
                                         :detail "Missing or invalid token"}})

                   (empty? (set/intersection roles required))
                   (sc/terminate ctx
                                 {:status 403
                                  :headers {"content-type" "application/json"}
                                  :body {:title "FORBIDDEN"
                                         :type "auth/forbidden"
                                         :status 403
                                         :detail "Insufficient privileges"}})

                   :else
                   ctx)))))})
