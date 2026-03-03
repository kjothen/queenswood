(ns lib.api)

(def ^:private api-key (atom nil))

(defn- admin-token [] (.-VITE_ADMIN_API_KEY (.-env js/import.meta)))

(defn- parse-response
  [res]
  (-> (.json res)
      (.then (fn [body] #js {:http-status (.-status res) :body body}))))

(defn init
  []
  (->
    (js/fetch "/v1/organizations"
              #js {:method "POST"
                   :headers #js {"Content-Type" "application/json"
                                 "Authorization" (str "Bearer " (admin-token))}
                   :body (js/JSON.stringify #js {"name" "dev-org"})})
    (.then (fn [res]
             (when-not (.-ok res)
               (throw (js/Error. (str "Failed to create org: "
                                      (.-status res)))))
             (.json res)))
    (.then (fn [body]
             (let [raw-key (aget body "raw-key")] (reset! api-key raw-key))))))

(defn create-account
  [{:strs [customer-id name currency]}]
  (-> (js/fetch "/v1/accounts"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify #js {"customer-id" customer-id
                                                   "name" name
                                                   "currency" currency})})
      (.then parse-response)))

(defn close-account
  [account-id]
  (-> (js/fetch (str "/v1/accounts/" account-id "/close")
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " @api-key)
                                   "Idempotency-Key" (str (random-uuid))}})
      (.then parse-response)))

(defn list-accounts
  [query-string]
  (let [url (if query-string (str "/v1/accounts?" query-string) "/v1/accounts")]
    (-> (js/fetch url
                  #js {:headers #js {"Authorization" (str "Bearer " @api-key)}})
        (.then parse-response))))
