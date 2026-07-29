(ns com.repldriven.queenswood.clearbank-simulator.webhooks.components
  (:require
    [com.repldriven.queenswood.clearbank-simulator.webhooks.examples :as
     examples]

    [com.repldriven.queenswood.clearbank-simulator.schema :refer
     [components-registry]]))

(def Webhook
  [:map
   {:json-schema/example examples/Webhook}
   [:type string?]
   [:url string?]])

(def WebhookList
  [:map
   {:json-schema/example examples/WebhookList}
   [:webhooks [:vector [:ref "Webhook"]]]])

(def RegisterWebhookRequest
  [:map
   {:json-schema/example examples/RegisterWebhookRequest}
   [:type string?]
   [:url string?]])

(def registry
  (components-registry [#'Webhook #'WebhookList #'RegisterWebhookRequest]))
