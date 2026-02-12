(ns com.repldriven.mono.symmetric-key-api.identities.routes
  (:require
    [com.repldriven.mono.symmetric-key-api.identities.handlers :as handlers]))

(def routes
  [["/identities/{identity-id}/keys"
    {:get {:summary "List symmetric keys for an identity"
           :parameters {:path {:identity-id string?}}
           :responses {200 {:body [:map [:data [:vector :any]]]}}
           :handler handlers/list-keys}}]
   ["/identities/{identity-id}/keys/{key-id}"
    {:get {:summary "Get a specific symmetric key"
           :parameters {:path {:identity-id string? :key-id string?}}
           :responses {200 {:body [:map [:data :any]]}}
           :handler handlers/get-key}}]])
