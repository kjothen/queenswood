(ns com.repldriven.mono.bank-api.auth
  "Three-path authentication. (1) A stop-gap env-var admin bearer
  retained for `bank-test-api-scenarios` fixtures and operator escape
  hatches. (2) A Keycloak-issued service JWT minted by a tenant's
  service-account client (`client_credentials` flow); principal type
  `:service`. (3) A Keycloak-issued user JWT minted by either the
  `queenswood-console` SPA against the `queenswood` realm (org
  admins/members) or the `queenswood-app` SPA against the
  `queenswood-ops` realm (Queenswood operators); principal type
  `:user`. JWT verification dispatches across multiple
  `identity-provider` instances keyed by the unverified `iss` claim,
  then the verified `azp` claim discriminates user vs service. The
  user path always upserts a `bank-user` row on first sign-in so
  every authenticated human has a stable platform-identity record."
  (:require
    [com.repldriven.mono.bank-api.shared.claims :as claims]
    [com.repldriven.mono.bank-membership.interface :as memberships]
    [com.repldriven.mono.bank-user.interface :as users]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface
     :as identity-provider]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as util]

    [sieppari.context :as sc]

    [clojure.set :as set]
    [clojure.string :as str])
  (:import
    (java.util Base64)))

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
  ;; The env-var admin bearer stays alongside the queenswood-ops
  ;; realm + ops user JWT path. Tests and the bootstrap deploy chain
  ;; still depend on it.
  {:principal-type :admin
   :principal-id "admin"
   :organization-id (:internal-organization-id request)
   :roles #{:admin :org}})

(defn- service-auth
  "Map a verified service-JWT claims map to the request auth context.
  The client_id (== organization-id by convention) appears as `:azp`.
  When the realm's role mapper has flagged the service account with
  `admin`, the principal flips its `:organization-id` to the
  internal-org-id and picks up `:admin` alongside `:org` — same
  shape as the env-var admin-auth principal, so admin scenario
  tokens minted via `client_credentials` against the
  `queenswood-admin` client can stand in for the env-var bearer."
  [request claims]
  (let [realm-roles (->> (get-in claims [:realm_access :roles])
                         (map keyword)
                         (into #{}))
        is-admin? (contains? realm-roles :admin)]
    {:principal-type :service
     :principal-id (:azp claims)
     :organization-id (if is-admin?
                        (:internal-organization-id request)
                        (:azp claims))
     :roles (into #{:org} realm-roles)
     :token-jti (:jti claims)}))

(defn- nilable-result
  "Treat a `nil` or anomaly result as absence; pass other values
  through."
  [v]
  (when-not (or (nil? v) (error/anomaly? v)) v))

(defn- realm-access-roles
  "Project the JWT's `realm_access.roles` claim into a set of role
  keywords. Empty when the claim is absent (the SPA realm may not
  include a realm-roles mapper)."
  [claims]
  (into #{} (map keyword) (get-in claims [:realm_access :roles])))

(defn- user-auth
  "Resolve a verified user-JWT into the principal sum-type. Upserts
  the User on every authenticated request — idempotent on the (iss,
  sub) pair, so first sign-in creates the row and subsequent sign-ins
  refresh mutable claims (email / name / avatar) only when they've
  changed. Roles always include `:user`; `:admin` is added when the
  realm carries it via `realm_access.roles`; `:org` is added when the
  user has at least one membership OR is admin (so existing org-
  scoped routes accept ops JWTs). The principal's `:organization-id`
  defaults to the internal-org-id for admins, the first membership's
  org for normal users, nil otherwise."
  [request claims]
  (let [{:keys [record-db record-store internal-organization-id]} request
        txn {:record-db record-db :record-store record-store}
        user (nilable-result
              (users/upsert-by-sub txn (claims/claims->user-claims claims)))
        memberships (or (when user
                          (nilable-result
                           (memberships/list-by-user txn (:user-id user))))
                        [])
        realm-roles (realm-access-roles claims)
        is-admin? (contains? realm-roles :admin)
        primary (first memberships)]
    (util/assoc-some
     {:principal-type :user
      :principal-id (:user-id user)
      :issuer (:iss claims)
      :sub (:sub claims)
      :user user
      :claims claims
      :memberships memberships
      :roles (cond-> #{:user}
                     is-admin?
                     (conj :admin :org)
                     (seq memberships)
                     (conj :org))
      :token-jti (:jti claims)}
     :organization-id
     (cond is-admin?
           internal-organization-id
           primary
           (:organization-id primary)))))

(defn- decode-unverified-payload
  "Best-effort base64url-decode of a JWT's middle segment into the
  payload claims map. Returns nil on any failure — callers should
  treat that the same as an unverifiable token."
  [^String jwt-string]
  (try
    (let [parts (str/split jwt-string #"\." 3)
          payload-bytes (.decode (Base64/getUrlDecoder)
                                 ^String (second parts))
          payload-str (String. payload-bytes "UTF-8")
          parsed (json/read-str payload-str :key-fn keyword)]
      (when (map? parsed) parsed))
    (catch Exception _ nil)))

(defn- unverified-issuer
  "Pull the `iss` claim out of the JWT payload WITHOUT signature
  verification — used only to pick which identity-provider should be
  asked to verify. The verifier still rejects the token if iss
  doesn't match its expected issuer, so a forged iss can't gain
  access to a realm whose JWKS it can't satisfy."
  [jwt-string]
  (:iss (decode-unverified-payload jwt-string)))

(defn- pick-provider
  "Find the identity-provider instance whose configured issuer matches
  the JWT's (unverified) iss claim. Returns nil when none match."
  [providers iss]
  (some (fn [p] (when (= iss (identity-provider/get-issuer p)) p))
        providers))

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  token (extract-bearer request)
                  {:keys [admin-api-key user-client-ids identity-providers
                          expected-audiences]}
                  request]
              (cond
               (nil? token)
               ctx

               (admin? token admin-api-key)
               (assoc-in ctx [:request :auth] (admin-auth request))

               :else
               (let [iss (unverified-issuer token)
                     provider (pick-provider identity-providers iss)
                     claims (when provider
                              (identity-provider/verify-token
                               provider
                               token
                               {:expected-audiences (set expected-audiences)}))]
                 (cond
                  (nil? provider)
                  (do (log/warn "JWT verification rejected: unknown issuer"
                                (pr-str iss))
                      ctx)

                  (not (map? claims))
                  (do (log/warn "JWT verification rejected:"
                                (:message (error/payload claims))
                                "iss:" (pr-str iss))
                      ctx)

                  (contains? (set user-client-ids) (:azp claims))
                  (assoc-in ctx
                   [:request :auth]
                   (user-auth request claims))

                  :else
                  (assoc-in ctx
                   [:request :auth]
                   (service-auth request claims)))))))})

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
