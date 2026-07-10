(ns com.repldriven.mono.bank-api.bank.routes
  (:require
    [com.repldriven.mono.bank-api.bank.examples :refer
     [BankLimitExceeded]]
    [com.repldriven.mono.bank-api.bank.commands :as bank-commands]
    [com.repldriven.mono.bank-api.bank.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/banks"
    {:openapi {:tags ["Banks"] :security [{"bearerAuth" ["admin"]}]}}
    [""
     {:get {:summary "Retrieve banks"
            :openapi {:operationId "RetrieveBanks"}
            :responses {200 {:body [:ref "BankList"]}}
            :handler queries/list-banks}
      :post {:summary "Create a new bank"
             :openapi {:operationId "CreateBank" :requestBody {:required true}}
             :parameters {:body [:ref "CreateBankRequest"]}
             :responses {201 {:body [:ref "CreateBankResponse"]}
                         422 (ErrorResponse [#'BankLimitExceeded])}
             :handler bank-commands/create-bank}}]]])
