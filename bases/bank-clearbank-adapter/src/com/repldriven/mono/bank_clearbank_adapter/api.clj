(ns com.repldriven.mono.bank-clearbank-adapter.api
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.handlers :as handlers]

    [com.repldriven.mono.bank-clearbank.interface :as clearbank]

    [com.repldriven.mono.server.interface :as server]

    [malli.core :as m]
    [malli.transform :as mt]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(defn- ->provider
  [base-transformer]
  (reify
   malli-coercion/TransformationProvider
     (-transformer [_ {:keys [strip-extra-keys default-values]}]
       (mt/transformer
        (when strip-extra-keys
          (mt/strip-extra-keys-transformer))
        base-transformer
        (when default-values
          (mt/default-value-transformer))))))

(def ^:private coercion
  (malli-coercion/create
   {:transformers {:body {:default (->provider (mt/json-transformer))}
                   :string {:default (->provider (mt/string-transformer))}
                   :response {:default (->provider nil)}}
    :options {:registry (merge (m/default-schemas)
                               clearbank/webhook-components-registry)}}))

(defn- routes
  [ctx]
  [["/webhooks"
    {:openapi {:tags ["Webhooks"]}
     :interceptors (:interceptors ctx)}
    ["/transaction-settled"
     {:post
      {:summary "Receive a TransactionSettled webhook from ClearBank"
       :openapi {:operationId "TransactionSettled"}
       :parameters {:body [:ref "TransactionSettledWebhook"]}
       :responses {200 {:body [:map
                               [:Nonce int?]]}}
       :handler (handlers/transaction-settled nil)}}]
    ["/transaction-rejected"
     {:post
      {:summary "Receive a TransactionRejected webhook from ClearBank"
       :openapi {:operationId "TransactionRejected"}
       :parameters {:body [:ref "TransactionRejectedWebhook"]}
       :responses {200 {:body [:map
                               [:Nonce int?]]}}
       :handler (handlers/transaction-rejected nil)}}]
    ["/payment-message-assessment-failed"
     {:post
      {:summary "Receive a PaymentMessageAssessmentFailed webhook"
       :openapi {:operationId "PaymentMessageAssessmentFailed"}
       :parameters {:body [:map
                           [:Type string?]
                           [:Version int?]
                           [:Payload map?]
                           [:Nonce int?]]}
       :responses {200 {:body [:map [:Nonce int?]]}}
       :handler (handlers/payment-message-assessment-failed nil)}}]
    ["/inbound-held-transaction"
     {:post
      {:summary "Receive an InboundHeldTransaction webhook"
       :openapi {:operationId "InboundHeldTransaction"}
       :parameters {:body [:map
                           [:Type string?]
                           [:Version int?]
                           [:Payload map?]
                           [:Nonce int?]]}
       :responses {200 {:body [:map
                               [:Nonce int?]]}}
       :handler (handlers/inbound-held-transaction nil)}}]]])

(defn app
  [ctx]
  (http/ring-handler
   (http/router (routes ctx)
                (assoc-in server/standard-router-data
                 [:data :coercion]
                 coercion))
   (ring/routes (ring/create-default-handler))
   server/standard-executor))
