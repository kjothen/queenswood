(ns com.repldriven.mono.bank-clearbank-adapter.webhook.routes
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.webhook.handlers
     :as handlers]))

(def routes
  [["/webhooks"
    {:openapi {:tags ["Webhooks"]}}
    ["/transaction-settled"
     {:post {:summary "Receive a TransactionSettled webhook from ClearBank"
             :openapi {:operationId "TransactionSettled"}
             :parameters {:body [:ref "TransactionSettledWebhook"]}
             :responses {200 {:body [:map
                                     [:Nonce int?]]}}
             :handler (handlers/transaction-settled nil)}}]
    ["/transaction-rejected"
     {:post {:summary "Receive a TransactionRejected webhook from ClearBank"
             :openapi {:operationId "TransactionRejected"}
             :parameters {:body [:ref "TransactionRejectedWebhook"]}
             :responses {200 {:body [:map
                                     [:Nonce int?]]}}
             :handler (handlers/transaction-rejected nil)}}]
    ["/payment-message-assessment-failed"
     {:post {:summary "Receive a PaymentMessageAssessmentFailed webhook"
             :openapi {:operationId "PaymentMessageAssessmentFailed"}
             :parameters {:body [:map
                                 [:Type string?]
                                 [:Version int?]
                                 [:Payload map?]
                                 [:Nonce int?]]}
             :responses {200 {:body [:map [:Nonce int?]]}}
             :handler (handlers/payment-message-assessment-failed nil)}}]
    ["/inbound-held-transaction"
     {:post {:summary "Receive an InboundHeldTransaction webhook"
             :openapi {:operationId "InboundHeldTransaction"}
             :parameters {:body [:map
                                 [:Type string?]
                                 [:Version int?]
                                 [:Payload map?]
                                 [:Nonce int?]]}
             :responses {200 {:body [:map
                                     [:Nonce int?]]}}
             :handler (handlers/inbound-held-transaction nil)}}]
    ["/inbound-cop-request-received"
     {:post {:summary "Receive an InboundCopRequestReceived webhook"
             :openapi {:operationId "InboundCopRequestReceived"}
             :parameters {:body [:ref "InboundCopRequestReceivedWebhook"]}
             :responses {200 {:body [:map
                                     [:matchResult
                                      [:enum "Match" "CloseMatch"
                                       "NoMatch" "Unavailable"]]
                                     [:actualName {:optional true}
                                      [:maybe string?]]
                                     [:reasonCode {:optional true}
                                      [:maybe string?]]
                                     [:reason {:optional true}
                                      [:maybe string?]]]}}
             :handler (handlers/inbound-cop-request-received nil)}}]]])
