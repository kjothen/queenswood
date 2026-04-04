(ns com.repldriven.mono.bank-clearbank-simulator.webhooks.handlers)

(def webhook-types
  {"TransactionSettled" "/webhooks/transaction-settled"
   "TransactionRejected" "/webhooks/transaction-rejected"
   "PaymentMessageAssessmentFailed"
   "/webhooks/payment-message-assessment-failed"
   "InboundHeldTransaction" "/webhooks/inbound-held-transaction"})

(defn list-webhooks
  [config]
  (fn [_request]
    (let [registered @(:webhooks config)]
      {:status 200
       :body {:webhooks (mapv (fn [[t u]] {:type t :url u})
                              registered)}})))

(defn register-webhook
  [config]
  (fn [request]
    (let [{:keys [type url]} (get-in request [:parameters :body])]
      (if-not (contains? webhook-types type)
        {:status 400
         :body {:title "BAD_REQUEST"
                :type "webhook/unknown-type"
                :status 400
                :detail (str "Unknown webhook type: " type
                             ". Valid types: "
                             (keys webhook-types))}}
        (do (swap! (:webhooks config) assoc type url)
            {:status 201
             :body {:type type :url url}})))))

(defn deregister-webhook
  [config]
  (fn [request]
    (let [type (get-in request [:parameters :path :type])]
      (if-not (contains? @(:webhooks config) type)
        {:status 404
         :body {:title "NOT_FOUND"
                :type "webhook/not-found"
                :status 404
                :detail (str "No webhook registered for type: "
                             type)}}
        (do (swap! (:webhooks config) dissoc type)
            {:status 204
             :body nil})))))
