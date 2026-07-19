(ns com.repldriven.mono.bank-api.bank.routes
  (:require
    [com.repldriven.mono.bank-api.bank.examples :refer
     [BankLimitExceeded BankNotFound BankInvalidStatus BankUnknownTier]]
    [com.repldriven.mono.bank-api.bank.commands :as bank-commands]
    [com.repldriven.mono.bank-api.bank.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]))

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
             :handler bank-commands/create-bank}}]
    ["/{bank-id}"
     {:parameters {:path {:bank-id [:ref "BankId"]}}}
     ["/change-tier"
      {:post {:summary "Change a bank's tier"
              :openapi {:operationId "ChangeBankTier"
                        :requestBody {:required true}
                        :parameters ^:replace [shared.parameters/ref-bank-id]}
              :parameters {:body [:ref "ChangeBankTierRequest"]}
              :responses {200 {:body [:ref "ChangeBankTierResponse"]}
                          404 (ErrorResponse [#'BankNotFound])
                          409 (ErrorResponse [#'BankInvalidStatus])
                          422 (ErrorResponse [#'BankUnknownTier])}
              :handler bank-commands/change-bank-tier}}]]]])
