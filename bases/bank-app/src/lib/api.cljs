(ns lib.api)

(def ^:private api-key (atom nil))
(def ^:private admin-key-store "mono-admin-api-key")
(def ^:private orgs-store "queenswood-org-credentials")

(defn admin-token
  "Browser-side admin API key. Read from localStorage first; fall
  back to the Vite-injected build-time env var as a dev convenience
  so a developer who set VITE_MONO_ADMIN_API_KEY on the host doesn't
  also have to paste it into the login screen."
  []
  (or (.getItem js/localStorage admin-key-store)
      (.-VITE_MONO_ADMIN_API_KEY (.-env js/import.meta))))

(defn save-admin-token
  "Persist the admin API key to localStorage. Called by the login
  screen on submit."
  [token]
  (.setItem js/localStorage admin-key-store token))

(defn clear-admin-token
  "Wipe the admin bearer and any per-org credentials captured during
  the session so a new admin login starts from scratch."
  []
  (.removeItem js/localStorage admin-key-store)
  (.removeItem js/localStorage orgs-store)
  (reset! api-key nil))

(defn- parse-response
  [res]
  (-> (.json res)
      (.then (fn [body] #js {:http-status (.-status res) :body body}))))

(defn- load-org-credentials
  []
  (let [raw (.getItem js/localStorage orgs-store)]
    (if raw (js/JSON.parse raw) #js {})))

(defn- save-org-credentials
  "Stash an org's Keycloak client_id + client_secret + status so
  set-org can mint JWTs for it later."
  [org-id client-id client-secret status]
  (let [store (load-org-credentials)]
    (aset store
          org-id
          #js {:client-id client-id
               :client-secret client-secret
               :status status})
    (.setItem js/localStorage orgs-store (js/JSON.stringify store))))

(defn- exchange-token
  "POST to /oauth/token with client_credentials. Resolves with the
  access_token string."
  [client-id client-secret status]
  (let [scope (if (= status "live") "queenswood-api-live" "queenswood-api-test")
        params (str "grant_type=client_credentials"
                    "&client_id=" (js/encodeURIComponent client-id)
                    "&client_secret=" (js/encodeURIComponent client-secret)
                    "&scope=" scope)]
    (-> (js/fetch "/oauth/token"
                  #js {:method "POST"
                       :headers
                       #js {"Content-Type" "application/x-www-form-urlencoded"}
                       :body params})
        (.then (fn [res] (.json res)))
        (.then (fn [body] (.-access_token body))))))

(defn set-org
  "Switch the bearer used by org-scoped requests to a JWT minted for
  `org-id` via /oauth/token. Returns a Promise that resolves once the
  token is in place; callers that immediately fetch org-scoped data
  must await it. Falls back to the admin bearer if no credentials are
  stored for the org (e.g. it was created in a different session)."
  [org-id]
  (let [creds (aget (load-org-credentials) org-id)]
    (if (and creds (.-client-id creds))
      (-> (exchange-token (.-client-id creds)
                          (.-client-secret creds)
                          (.-status creds))
          (.then (fn [token]
                   (reset! api-key (or token (admin-token)))
                   token)))
      (do (reset! api-key (admin-token))
          (js/Promise.resolve nil)))))

(defn create-organization
  [org-name org-status tier currencies]
  (-> (js/fetch "/v1/organizations"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        (admin-token))}
                     :body (js/JSON.stringify
                            (clj->js {"name" org-name
                                      "status" org-status
                                      "tier" tier
                                      "currencies" currencies}))})
      (.then parse-response)
      (.then (fn [res]
               (let [status (aget res "http-status")]
                 (when (and (>= status 200) (< status 300))
                   (let [body (.-body res)
                         org-id (aget body "organization-id")
                         client-id (aget body "client-id")
                         client-secret (aget body "client-secret")]
                     (when (and client-id client-secret)
                       (save-org-credentials org-id
                                             client-id
                                             client-secret
                                             org-status))))
                 res)))))

(defn list-organizations
  []
  (-> (js/fetch "/v1/organizations"
                #js {:headers #js {"Authorization" (str "Bearer "
                                                        (admin-token))}})
      (.then parse-response)))

(defn create-party
  [data]
  (let [{:strs [display-name given-name middle-names family-name date-of-birth
                nationality national-identifier]}
        (js->clj data)
        body (cond-> {"type" "person"
                      "display-name" display-name
                      "given-name" given-name
                      "family-name" family-name
                      "date-of-birth" date-of-birth
                      "nationality" nationality}
                     middle-names
                     (assoc "middle-names" middle-names)
                     national-identifier
                     (assoc "national-identifier"
                            national-identifier))]
    (-> (js/fetch "/v1/parties"
                  #js {:method "POST"
                       :headers #js {"Content-Type" "application/json"
                                     "Authorization" (str "Bearer " @api-key)
                                     "Idempotency-Key" (str (random-uuid))}
                       :body (js/JSON.stringify (clj->js body))})
        (.then parse-response))))

(defn list-parties
  [query-string]
  (let [url (if query-string (str "/v1/parties?" query-string) "/v1/parties")]
    (-> (js/fetch url
                  #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
        (.then parse-response))))

(defn list-payee-checks
  [query-string]
  (let [url (if query-string
              (str "/v1/payee-checks?" query-string)
              "/v1/payee-checks")]
    (-> (js/fetch url
                  #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
        (.then parse-response))))

(defn open-cash-account
  [data]
  (let [{:strs [party-id name currency product-id]} (js->clj data)]
    (-> (js/fetch "/v1/cash-accounts"
                  #js {:method "POST"
                       :headers #js {"Content-Type" "application/json"
                                     "Authorization" (str "Bearer " @api-key)
                                     "Idempotency-Key" (str (random-uuid))}
                       :body (js/JSON.stringify #js {"party-id" party-id
                                                     "name" name
                                                     "currency" currency
                                                     "product-id" product-id})})
        (.then parse-response))))

(defn close-cash-account
  [account-id]
  (-> (js/fetch (str "/v1/cash-accounts/" account-id "/close")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)
                                   "Idempotency-Key" (str (random-uuid))}})
      (.then parse-response)))

(def ^:private embed-params "embed[balances]=true&embed[transactions]=true")

(defn list-cash-accounts
  [query-string]
  (let [url (if query-string
              (str "/v1/cash-accounts?" query-string "&" embed-params)
              (str "/v1/cash-accounts?" embed-params))]
    (-> (js/fetch url
                  #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
        (.then parse-response))))

(defn get-cash-account
  [account-id]
  (-> (js/fetch (str "/v1/cash-accounts/" account-id "?" embed-params)
                #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn- product-request-body
  [data]
  (let [{:strs [name product-type balance-sheet-side allowed-currencies
                balance-products allowed-payment-address-schemes
                interest-rate-bps]}
        (js->clj data)]
    (cond-> {"name" name
             "product-type" product-type
             "balance-sheet-side" balance-sheet-side}
            (seq allowed-currencies)
            (assoc "allowed-currencies" allowed-currencies)
            (seq balance-products)
            (assoc "balance-products" balance-products)
            (seq allowed-payment-address-schemes)
            (assoc "allowed-payment-address-schemes"
                   allowed-payment-address-schemes)
            interest-rate-bps
            (assoc "interest-rate-bps" interest-rate-bps))))

(defn create-cash-account-product
  [data]
  (-> (js/fetch "/v1/cash-account-products"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)}
                     :body (js/JSON.stringify
                            (clj->js (product-request-body data)))})
      (.then parse-response)))

(defn list-cash-account-products
  []
  (-> (js/fetch "/v1/cash-account-products"
                #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn publish-cash-account-product
  [product-id version-id]
  (-> (js/fetch (str "/v1/cash-account-products/"
                     product-id
                     "/versions/"
                     version-id
                     "/publish")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn open-cash-account-product-draft
  [product-id data]
  (-> (js/fetch (str "/v1/cash-account-products/" product-id "/versions")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)}
                     :body (js/JSON.stringify
                            (clj->js (product-request-body data)))})
      (.then parse-response)))

(defn update-cash-account-product-draft
  [product-id version-id data]
  (-> (js/fetch (str "/v1/cash-account-products/"
                     product-id
                     "/versions/"
                     version-id)
                #js {:method "PUT"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)}
                     :body (js/JSON.stringify
                            (clj->js (product-request-body data)))})
      (.then parse-response)))

(defn discard-cash-account-product-draft
  [product-id version-id]
  (-> (js/fetch (str "/v1/cash-account-products/"
                     product-id
                     "/versions/"
                     version-id)
                #js {:method "DELETE"
                     :headers #js {"Authorization" (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn list-balances
  [account-id]
  (-> (js/fetch (str "/v1/cash-accounts/" account-id "/balances")
                #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn list-transactions
  [account-id]
  (-> (js/fetch (str "/v1/cash-accounts/" account-id "/transactions")
                #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn simulate-inbound-transfer
  [org-id account-id amount currency]
  (-> (js/fetch (str "/v1/simulate/organizations/"
                     org-id
                     "/inbound-transfer")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        (admin-token))
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify
                            #js {"account-id" account-id
                                 "amount" amount
                                 "currency" currency})})
      (.then parse-response)))

(defn simulate-accrue
  [org-id as-of-date]
  (-> (js/fetch (str "/v1/simulate/organizations/"
                     org-id
                     "/accrue")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        (admin-token))
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify
                            #js {"as-of-date" as-of-date})})
      (.then parse-response)))

(defn simulate-capitalize
  [org-id as-of-date]
  (-> (js/fetch (str "/v1/simulate/organizations/"
                     org-id
                     "/capitalize")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        (admin-token))
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify
                            #js {"as-of-date" as-of-date})})
      (.then parse-response)))

(defn submit-internal-payment
  [debtor-account-id creditor-account-id currency amount reference]
  (-> (js/fetch "/v1/payments/internal"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        @api-key)
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify
                            (clj->js
                             (cond-> {"debtor-account-id" debtor-account-id
                                      "creditor-account-id" creditor-account-id
                                      "currency" currency
                                      "amount" amount}
                                     reference
                                     (assoc "reference" reference))))})
      (.then parse-response)))

(defn check-payee
  [creditor-name sort-code account-number account-type]
  (-> (js/fetch "/v1/payee-checks"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        @api-key)}
                     :body (js/JSON.stringify
                            (clj->js
                             {"creditor-name" creditor-name
                              "account" {"sort-code" sort-code
                                         "account-number" account-number}
                              "account-type" account-type}))})
      (.then parse-response)))

(defn submit-outbound-payment
  [debtor-account-id creditor-bban creditor-name
   currency amount scheme reference]
  (-> (js/fetch "/v1/payments/outbound"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer "
                                                        @api-key)
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify
                            (clj->js
                             (cond-> {"debtor-account-id" debtor-account-id
                                      "creditor-bban" creditor-bban
                                      "creditor-name" creditor-name
                                      "currency" currency
                                      "amount" amount
                                      "scheme" scheme}
                                     reference
                                     (assoc "reference" reference))))})
      (.then parse-response)))

(defn list-tiers
  []
  (-> (js/fetch "/v1/tiers"
                #js {:headers #js {"Authorization"
                                   (str "Bearer "
                                        (admin-token))}})
      (.then parse-response)))

(defn list-policies
  []
  (-> (js/fetch "/v1/policies"
                #js {:headers #js {"Authorization"
                                   (str "Bearer "
                                        (admin-token))}})
      (.then parse-response)))

(defn get-policy
  [policy-id]
  (-> (js/fetch (str "/v1/policies/" policy-id)
                #js {:headers #js {"Authorization"
                                   (str "Bearer "
                                        (admin-token))}})
      (.then parse-response)))
