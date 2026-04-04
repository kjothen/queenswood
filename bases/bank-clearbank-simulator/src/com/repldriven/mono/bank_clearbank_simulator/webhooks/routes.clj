(ns com.repldriven.mono.bank-clearbank-simulator.webhooks.routes
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.webhooks.handlers
     :as handlers]))

(defn routes
  [config]
  [["/v1/webhooks"
    {:openapi {:tags ["Webhooks"]}}
    [""
     {:get {:summary "List registered webhooks"
            :openapi {:operationId "ListWebhooks"}
            :responses {200 {:body [:ref "WebhookList"]}}
            :handler (handlers/list-webhooks config)}
      :post {:summary "Register a webhook"
             :openapi {:operationId "RegisterWebhook"}
             :parameters {:body [:ref "RegisterWebhookRequest"]}
             :responses {201 {:body [:ref "Webhook"]}}
             :handler (handlers/register-webhook config)}}]
    ["/{type}"
     {:delete {:summary "Deregister a webhook"
               :openapi {:operationId "DeregisterWebhook"}
               :parameters {:path {:type string?}}
               :responses {204 {}}
               :handler (handlers/deregister-webhook config)}}]]])
