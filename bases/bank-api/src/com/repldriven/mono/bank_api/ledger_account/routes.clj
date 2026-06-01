(ns com.repldriven.mono.bank-api.ledger-account.routes
  (:require
    [com.repldriven.mono.bank-api.ledger-account.queries :as queries]
    [com.repldriven.mono.bank-api.ledger-account.examples :refer
     [LedgerAccountNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/ledger-accounts"
    {:openapi {:tags ["Ledger Accounts"] :security [{"bearerAuth" ["org"]}]}}
    [""
     {:get {:summary "Retrieve ledger accounts"
            :openapi {:operationId "RetrieveLedgerAccounts"}
            :responses {200 {:body [:ref "LedgerAccountList"]}}
            :handler queries/list-ledger-accounts}}]
    ["/{account-id}"
     {:parameters {:path {:account-id [:ref "LedgerAccountId"]}}}
     [""
      {:get {:summary "Retrieve a ledger account"
             :openapi {:operationId "RetrieveLedgerAccount"}
             :responses {200 {:body [:ref "LedgerAccount"]}
                         404 (ErrorResponse [#'LedgerAccountNotFound])}
             :handler queries/get-ledger-account}}]
     ["/balances"
      {:get {:summary "Retrieve ledger account balances"
             :openapi {:operationId "RetrieveLedgerAccountBalances"}
             :responses {200 {:body [:ref "LedgerBalanceList"]}
                         404 (ErrorResponse [#'LedgerAccountNotFound])}
             :handler queries/list-balances}}]]]])