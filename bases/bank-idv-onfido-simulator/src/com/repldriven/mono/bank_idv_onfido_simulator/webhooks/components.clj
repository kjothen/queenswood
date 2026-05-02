(ns com.repldriven.mono.bank-idv-onfido-simulator.webhooks.components
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.schema :as schema]
    [com.repldriven.mono.bank-idv-onfido-simulator.webhooks.examples
     :as examples]))

(def RegisterWebhookRequest
  [:map
   {:json-schema/example examples/RegisterWebhookRequest}
   [:url string?]])

(def Webhook
  [:map
   {:json-schema/example examples/Webhook}
   [:id string?]
   [:url string?]])

(def WebhookList
  [:map
   {:json-schema/example examples/WebhookList}
   [:webhooks [:vector [:ref "Webhook"]]]])

(def registry
  (schema/components-registry [#'RegisterWebhookRequest #'Webhook
                               #'WebhookList]))
