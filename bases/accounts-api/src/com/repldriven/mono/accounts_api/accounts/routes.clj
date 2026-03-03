(ns com.repldriven.mono.accounts-api.accounts.routes
  (:require
    [com.repldriven.mono.accounts-api.accounts.handlers :as handlers]

    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn routes
  [ctx]
  ["/v1"
   {:interceptors (concat telemetry/trace-span (:interceptors ctx))}
   ["/accounts"
    {:get {:summary "List accounts" :handler handlers/list-accounts}
     :post {:summary "Open a new account"
            :interceptors [telemetry/require-idempotency-key]
            :handler handlers/open-account}}]
   ["/accounts/{account-id}"
    {:get {:summary "Get account status" :handler handlers/get-account-status}}]
   ["/accounts/{account-id}/close"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Close an account" :handler handlers/close-account}}]])
