(ns com.repldriven.mono.bank-identity-provider.domain
  "Pure shaping of Keycloak Admin API requests and responses.")

(defn audience-for-status
  "Map an organization status to its Keycloak audience claim.
  `:organization-status-live` → `queenswood-api-live`; everything
  else (including `:organization-status-test`) → `queenswood-api-test`."
  [status]
  (if (= :organization-status-live status)
    "queenswood-api-live"
    "queenswood-api-test"))

(defn new-client-representation
  "Build the Keycloak ClientRepresentation JSON body for a per-org
  service-account client. Returns plain Clojure data ready for JSON
  encoding."
  [{:keys [organization-id name status]}]
  {:clientId organization-id
   :name (or name organization-id)
   :enabled true
   :protocol "openid-connect"
   :publicClient false
   :serviceAccountsEnabled true
   :standardFlowEnabled false
   :directAccessGrantsEnabled false
   :implicitFlowEnabled false
   :attributes {"access.token.lifespan" "3600"}
   :defaultClientScopes ["service-accounts"]
   :optionalClientScopes []
   ;; Audience hard-coded into the token via a protocol mapper that
   ;; the realm import provisions; we tag the client itself with the
   ;; per-env audience as a non-functional label too.
   :description (audience-for-status status)})

(defn parse-token-response
  "Pull `{:access-token :expires-in}` out of a Keycloak token
  response. Returns nil on malformed input."
  [body]
  (when (and (map? body) (:access_token body))
    {:access-token (:access_token body)
     :expires-in (or (:expires_in body) 60)}))

(defn parse-jwks
  "Pass-through that exists so callers can route through one place
  if Keycloak's JWKS response shape ever needs translation."
  [body]
  (when (and (map? body) (sequential? (:keys body)))
    body))

(defn admin-token-expired?
  "Return true if `cached` is nil or older than 80 % of its lifetime.
  `cached` is `{:access-token … :expires-in <seconds> :fetched-at <ms>}`."
  [cached now-ms]
  (or (nil? cached)
      (let [{:keys [expires-in fetched-at]} cached
            age-ms (- now-ms fetched-at)
            threshold-ms (* 0.8 1000 (or expires-in 60))]
        (>= age-ms threshold-ms))))

(defn jwks-stale?
  "Return true if cached JWKS is older than the configured TTL."
  [cached now-ms ttl-ms]
  (or (nil? cached)
      (>= (- now-ms (:fetched-at cached)) ttl-ms)))
