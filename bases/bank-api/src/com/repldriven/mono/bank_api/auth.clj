(ns com.repldriven.mono.bank-api.auth
  "Three-path authentication: a stop-gap env-var admin bearer (kept
  until human-auth fully replaces it), a Keycloak-issued JWT minted
  by a tenant service account (existing `client_credentials` flow),
  and a Keycloak-issued JWT minted by the `queenswood-console` SPA
  on behalf of a human User (Authorization Code + PKCE flow). The
  three paths share JWT verification via the `identity-provider`
  substrate and diverge on principal shape: admin → fixed admin
  context, service → `{:principal-type :service ...}`, user →
  `{:principal-type :user :user :memberships ...}` resolved against
  the `bank-user` + `bank-membership` bricks."
  (:require
    [com.repldriven.mono.bank-membership.interface :as memberships]
    [com.repldriven.mono.bank-user.interface :as users]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface
     :as identity-provider]
    [com.repldriven.mono.log.interface :as log]
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
  "Map a verified service-JWT claims map to the request auth context.
  The client_id (== organization-id by convention) appears as `:azp`."
  [claims]
  {:principal-type :service
   :principal-id (:azp claims)
   :organization-id (:azp claims)
   :roles (-> claims
              (get-in [:realm_access :roles])
              (->> (map keyword))
              (->> (into #{:org})))
   :token-jti (:jti claims)})

(defn- nilable-result
  "Treat a `nil` or anomaly result as absence; pass other values
  through. The bank-user/bank-membership reads we depend on return
  nil for missing records and an anomaly only on FDB failure; both
  surfaces collapse to \"no domain identity\" for the auth context."
  [v]
  (when-not (or (nil? v) (error/anomaly? v)) v))

(defn- user-auth
  "Resolve a verified user-JWT into the principal sum-type. The user
  record may be absent (first sign-in before onboarding) — in that
  case `:user-id`/`:user` are nil and only the `:user` role is
  granted, gating non-onboarding routes off until /v1/onboarding/me
  runs. With at least one membership the principal also carries
  `:org`, the default role existing tenant-scoped routes require."
  [request claims]
  (let [{:keys [record-db record-store]} request
        txn {:record-db record-db :record-store record-store}
        sub (:sub claims)
        user (nilable-result (users/find-by-keycloak-sub txn sub))
        memberships (or (when user
                          (nilable-result
                           (memberships/list-by-user txn (:user-id user))))
                        [])
        primary (first memberships)]
    {:principal-type :user
     :principal-id (:user-id user)
     :keycloak-sub sub
     :user user
     :claims claims
     :memberships memberships
     :organization-id (:organization-id primary)
     :roles (cond-> #{:user}
                    (seq memberships)
                    (conj :org))
     :token-jti (:jti claims)}))

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  token (extract-bearer request)
                  {:keys [admin-api-key console-client-id identity-provider
                          expected-audiences]}
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
                 (cond
                  (not (map? claims))
                  (do (log/warn "JWT verification rejected:"
                                (:message (error/payload claims))
                                "expected-audiences:" expected-audiences
                                "console-client-id:" console-client-id)
                      ctx)

                  (and console-client-id
                       (= console-client-id (:azp claims)))
                  (assoc-in ctx
                   [:request :auth]
                   (user-auth request claims))

                  :else
                  (assoc-in ctx
                   [:request :auth]
                   (service-auth claims)))))))})

(defn- required-roles
  "Derive the role set a route requires from its OpenAPI metadata.
  `bearerAuth` alone permits any authenticated principal; an
  `x-required-roles`-style extension narrows it (e.g. `[admin]` or
  `[user]`)."
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
