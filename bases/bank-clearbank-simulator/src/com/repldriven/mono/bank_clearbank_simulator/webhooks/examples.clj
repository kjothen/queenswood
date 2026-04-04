(ns com.repldriven.mono.bank-clearbank-simulator.webhooks.examples
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [examples-registry]]))

(def Webhook
  {:type "TransactionSettled"
   :url "https://example.com/webhooks/transaction-settled"})

(def WebhookList
  {:webhooks [{:type "TransactionSettled"
               :url "https://example.com/webhooks/transaction-settled"}
              {:type "PaymentMessageAssessmentFailed"
               :url "https://example.com/webhooks/assessment-failed"}]})

(def RegisterWebhookRequest
  {:type "TransactionSettled"
   :url "https://example.com/webhooks/transaction-settled"})

(def registry
  (examples-registry [#'Webhook #'WebhookList #'RegisterWebhookRequest]))
