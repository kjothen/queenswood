(ns com.repldriven.mono.bank-clearbank-simulator.webhooks.handlers)

(def webhook-types
  {"TransactionSettled" "/webhooks/transaction-settled"
   "TransactionRejected" "/webhooks/transaction-rejected"
   "PaymentMessageAssessmentFailed"
   "/webhooks/payment-message-assessment-failed"
   "InboundHeldTransaction" "/webhooks/inbound-held-transaction"
   "InboundCopRequestReceived" "/webhooks/inbound-cop-request-received"})

(defn list-webhooks
  [_config]
  (fn [request]
    (let [{:keys [webhooks sort-code]} request
          registered (get @webhooks sort-code {})]
      {:status 200
       :body {:webhooks (mapv (fn [[t u]] {:type t :url u})
                              registered)}})))

(defn register-webhook
  [_config]
  (fn [request]
    (let [{:keys [webhooks sort-code parameters]} request
          {:keys [body]} parameters
          {:keys [type url]} body]
      (if-not (contains? webhook-types type)
        {:status 400
         :body {:title "BAD_REQUEST"
                :type "webhook/unknown-type"
                :status 400
                :detail (str "Unknown webhook type: " type
                             ". Valid types: "
                             (keys webhook-types))}}
        (do (swap! webhooks assoc-in [sort-code type] url)
            {:status 201
             :body {:type type :url url}})))))

(defn deregister-webhook
  [_config]
  (fn [request]
    (let [{:keys [webhooks sort-code parameters]} request
          {:keys [path]} parameters
          {:keys [type]} path]
      (if-not (get-in @webhooks [sort-code type])
        {:status 404
         :body {:title "NOT_FOUND"
                :type "webhook/not-found"
                :status 404
                :detail (str "No webhook registered for type: "
                             type)}}
        (do (swap! webhooks update sort-code dissoc type)
            {:status 204
             :body nil})))))
