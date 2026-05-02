(ns com.repldriven.mono.bank-idv-onfido-simulator.webhooks.examples)

(def RegisterWebhookRequest
  {:url "http://onfido-adapter:8080/webhooks/onfido/check-completed"})

(def Webhook
  {:id "wh_aaaa-bbbb-cccc-dddd"
   :url "http://onfido-adapter:8080/webhooks/onfido/check-completed"})

(def WebhookList {:webhooks [Webhook]})
