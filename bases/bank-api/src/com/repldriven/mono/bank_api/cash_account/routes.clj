(ns com.repldriven.mono.bank-api.cash-account.routes
  (:require
    [com.repldriven.mono.bank-api.cash-account.commands :as commands]
    [com.repldriven.mono.bank-api.cash-account.queries :as queries]
    [com.repldriven.mono.bank-api.cash-account.examples :refer
     [CashAccountNotFound CashAccountAlreadyExists ProductNotPublished
      InvalidCurrency PartyNotFound ProductNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def ^:private list-cash-accounts-query-schema
  [:map {:closed true}
   [:embed {:optional true} [:ref "EmbedQuery"]]
   [:page {:optional true} [:ref "PageQuery"]]])

(def ^:private get-cash-account-query-schema
  [:map {:closed true}
   [:embed {:optional true} [:ref "EmbedQuery"]]])

(def routes
  [["/cash-accounts"
    {:openapi {:tags ["Cash Accounts"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve cash accounts"
            :openapi {:operationId "RetrieveCashAccounts"
                      :parameters shared.parameters/ref-page-and-embed}
            :parameters {:query list-cash-accounts-query-schema}
            :responses {200 {:body [:ref "CashAccountList"]}}
            :handler queries/list-cash-accounts}
      :post {:summary "Open a new cash account"
             :openapi {:operationId "CreateCashAccount"
                       :requestBody {:required true}
                       :parameters shared.parameters/ref-idempotency-key}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "CreateCashAccountRequest"]}
             :responses {200 {:body [:ref "CreateCashAccountResponse"]}
                         404 (ErrorResponse [#'PartyNotFound
                                             #'ProductNotFound])
                         422 (ErrorResponse [#'CashAccountAlreadyExists
                                             #'ProductNotPublished
                                             #'InvalidCurrency])}
             :handler commands/open-cash-account}}]
    ["/{account-id}" {:parameters {:path {:account-id [:ref "CashAccountId"]}}}
     [""
      {:get {:summary "Retrieve a cash account"
             :openapi {:operationId "RetrieveCashAccount"
                       :parameters shared.parameters/ref-embed}
             :parameters {:query get-cash-account-query-schema}
             :responses {200 {:body [:ref "CashAccount"]}
                         404 (ErrorResponse [#'CashAccountNotFound])}
             :handler queries/get-cash-account}}]
     ["/transactions"
      {:get {:summary "Retrieve account transactions"
             :openapi {:operationId "RetrieveAccountTransactions"}
             :responses {200 {:body [:ref "TransactionList"]}
                         404 (ErrorResponse [#'CashAccountNotFound])}
             :handler queries/list-transactions}}]
     ["/close"
      {:post {:summary "Close a cash account"
              :openapi {:operationId "CloseCashAccount"
                        :parameters shared.parameters/ref-idempotency-key}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref "CloseCashAccountResponse"]}
                          404 (ErrorResponse [#'CashAccountNotFound])}
              :handler commands/close-cash-account}}]]]])
