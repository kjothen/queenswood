(ns com.repldriven.mono.bank-api.balance.routes
  (:require
    [com.repldriven.mono.bank-api.balance.handlers :as handlers]
    [com.repldriven.mono.bank-api.balance.queries :as queries]
    [com.repldriven.mono.bank-api.balance.examples :refer
     [BalanceAlreadyExists BalanceNotFound]]
    [com.repldriven.mono.bank-api.cash-account-product.examples :refer
     [VersionNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/cash-accounts/{account-id}/balances"
    {:openapi {:tags ["Balances"] :security [{"orgAuth" []}]}
     :parameters {:path {:account-id [:ref "CashAccountId"]}}}
    [""
     {:get {:summary "Retrieve account balances"
            :openapi {:operationId "RetrieveBalances"}
            :responses {200 {:body [:ref "BalanceList"]}}
            :handler queries/list-balances}
      :post {:summary "Create a balance"
             :openapi {:operationId "CreateBalance"
                       :requestBody {:required true}}
             :parameters {:body [:ref "CreateBalanceRequest"]}
             :responses {201 {:body [:ref "CreateBalanceResponse"]}
                         409 (ErrorResponse [#'BalanceAlreadyExists])}
             :handler handlers/create-balance}}]
    ["/{balance-type}/{currency}/{balance-status}"
     {:get {:summary "Retrieve a balance"
            :openapi {:operationId "RetrieveBalance"}
            :parameters {:path {:balance-type [:ref "BalanceType"]
                                :currency [:ref "Currency"]
                                :balance-status [:ref "BalanceStatus"]}}
            :responses {200 {:body [:ref "Balance"]}
                        404 (ErrorResponse [#'BalanceNotFound])}
            :handler queries/get-balance}}]]
   ["/cash-account-products/{product-id}/versions/{version-id}/balance-products"
    {:openapi {:tags ["Balance Products"] :security [{"orgAuth" []}]}
     :parameters {:path {:product-id [:ref "ProductId"]
                         :version-id [:ref "VersionId"]}}}
    [""
     {:get {:summary "Retrieve balance products for a version"
            :openapi {:operationId "RetrieveBalanceProducts"}
            :responses {200 {:body [:ref "BalanceProductList"]}
                        404 (ErrorResponse [#'VersionNotFound])}
            :handler queries/list-balance-products}}]]])
