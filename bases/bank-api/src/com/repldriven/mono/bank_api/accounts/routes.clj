(ns com.repldriven.mono.bank-api.accounts.routes
  (:require
    [com.repldriven.mono.bank-api.accounts.commands :as commands]
    [com.repldriven.mono.bank-api.accounts.queries :as queries]

    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def ^:private list-accounts-query-schema
  [:map [(keyword "page[after]") {:optional true} string?]
   [(keyword "page[before]") {:optional true} string?]
   [(keyword "page[size]") {:optional true} string?]])

(def routes
  [["/accounts"
    {:get {:summary "List accounts"
           :openapi {:operationId "ListAccounts" :security [{"orgAuth" []}]}
           :parameters {:query list-accounts-query-schema}
           :responses {200 {:body [:ref "AccountList"]}}
           :handler queries/list-accounts}
     :post {:summary "Open a new account"
            :openapi {:operationId "OpenAccount" :security [{"orgAuth" []}]}
            :interceptors [telemetry/require-idempotency-key]
            :parameters {:body [:ref "OpenAccountRequest"]}
            :handler commands/open-account}}]
   ["/accounts/{account-id}"
    {:parameters {:path {:account-id [:ref "AccountId"]}}}
    [""
     {:get {:summary "Get an account"
            :openapi {:operationId "GetAccount" :security [{"orgAuth" []}]}
            :responses {200 {:body [:ref "Account"]}}
            :handler queries/get-account}}]
    ["/close"
     {:post {:summary "Close an account"
             :openapi {:operationId "CloseAccount" :security [{"orgAuth" []}]}
             :interceptors [telemetry/require-idempotency-key]
             :handler commands/close-account}}]]])
