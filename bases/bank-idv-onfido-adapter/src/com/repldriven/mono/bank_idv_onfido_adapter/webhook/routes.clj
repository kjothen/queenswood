(ns com.repldriven.mono.bank-idv-onfido-adapter.webhook.routes
  (:require
    [com.repldriven.mono.bank-idv-onfido-adapter.webhook.handlers
     :as handlers]))

(def routes
  [["/webhooks"
    {:openapi {:tags ["Webhooks"]}}
    ["/onfido/check-completed"
     {:post {:summary "Receive an Onfido check.completed webhook"
             :openapi {:operationId "OnfidoCheckCompleted"}
             :parameters {:body [:ref "CheckCompletedWebhook"]}
             :responses {200 {:body [:map [:received boolean?]]}}
             :handler (handlers/check-completed nil)}}]]])
