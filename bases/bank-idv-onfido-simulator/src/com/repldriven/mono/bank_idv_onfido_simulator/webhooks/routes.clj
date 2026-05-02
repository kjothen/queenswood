(ns com.repldriven.mono.bank-idv-onfido-simulator.webhooks.routes
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.webhooks.handlers
     :as handlers]))

(def routes
  [["/v3.6/webhooks"
    {:openapi {:tags ["Webhooks"]}}
    [""
     {:get {:summary "List registered webhooks"
            :openapi {:operationId "ListWebhooks"}
            :responses {200 {:body [:ref "WebhookList"]}}
            :handler (handlers/list-webhooks nil)}
      :post {:summary "Register a webhook"
             :openapi {:operationId "RegisterWebhook"}
             :parameters {:body [:ref "RegisterWebhookRequest"]}
             :responses {201 {:body [:ref "Webhook"]}}
             :handler (handlers/register-webhook nil)}}]
    ["/{id}"
     {:delete {:summary "Deregister a webhook"
               :openapi {:operationId "DeregisterWebhook"}
               :parameters {:path {:id string?}}
               :responses {204 {} 404 {:body [:ref "ErrorResponse"]}}
               :handler (handlers/deregister-webhook nil)}}]]])
