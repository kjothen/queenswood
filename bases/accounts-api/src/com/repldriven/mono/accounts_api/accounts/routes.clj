(ns com.repldriven.mono.accounts-api.accounts.routes
  (:require
    [com.repldriven.mono.accounts-api.accounts.handlers :as handlers]

    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn routes
  [ctx]
  ["/v1" {:interceptors (concat telemetry/trace-span (:interceptors ctx))}
   ["/accounts"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Open a new account" :handler handlers/open-account}}]
   ["/accounts/{account-id}"
    {:get {:summary "Get account status" :handler handlers/get-account-status}}]
   ["/accounts/{account-id}/close"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Close an account" :handler handlers/close-account}}]
   ["/accounts/{account-id}/reopen"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Reopen an account" :handler handlers/reopen-account}}]
   ["/accounts/{account-id}/suspend"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Suspend an account" :handler handlers/suspend-account}}]
   ["/accounts/{account-id}/unsuspend"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Unsuspend an account"
            :handler handlers/unsuspend-account}}]
   ["/accounts/{account-id}/archive"
    {:interceptors [telemetry/require-idempotency-key]
     :post {:summary "Archive an account" :handler handlers/archive-account}}]])
