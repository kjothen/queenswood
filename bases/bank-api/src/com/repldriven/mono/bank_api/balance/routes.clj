(ns com.repldriven.mono.bank-api.balance.routes
  (:require
    [com.repldriven.mono.bank-api.balance.handlers :as handlers]
    [com.repldriven.mono.bank-api.balance.queries :as queries]
    [com.repldriven.mono.bank-api.balance.examples :refer
     [BalanceAlreadyExists BalanceNotFound]]
    [com.repldriven.mono.bank-api.cash-account.examples :refer
     [CashAccountNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/cash-accounts/{account-id}/balances"
    {:openapi {:tags ["Balances"] :security [{"orgAuth" []}]}
     :parameters {:path {:account-id [:ref "CashAccountId"]}}}
    [""
     {:get {:summary "Retrieve account balances"
            :openapi {:operationId "RetrieveBalances"}
            :responses {200 {:body [:ref "BalanceList"]}
                        404 (ErrorResponse [#'CashAccountNotFound])}
            :handler queries/list-balances}
      :post {:summary "Create a balance"
             :openapi {:operationId "CreateBalance"
                       :requestBody {:required true}}
             :parameters {:body [:ref "CreateBalanceRequest"]}
             :responses {201 {:body [:ref "CreateBalanceResponse"]}
                         404 (ErrorResponse [#'CashAccountNotFound])
                         409 (ErrorResponse [#'BalanceAlreadyExists])}
             :handler handlers/create-balance}}]
    ["/{balance-type}/{currency}/{balance-status}"
     {:get {:summary "Retrieve a balance"
            :openapi {:operationId "RetrieveBalance"}
            :parameters {:path {:balance-type [:ref "BalanceType"]
                                :currency [:ref "Currency"]
                                :balance-status [:ref "BalanceStatus"]}}
            :responses {200 {:body [:ref "Balance"]}
                        404 (ErrorResponse [#'CashAccountNotFound
                                            #'BalanceNotFound])}
            :handler queries/get-balance}}]]])
