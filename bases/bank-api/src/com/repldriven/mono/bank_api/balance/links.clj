(ns com.repldriven.mono.bank-api.balance.links
  "OpenAPI 3 `links` objects for balance responses.")

(def from-balance
  "Links available on a `CreateBalanceResponse` (which is a `Balance`)."
  {"GetBalance" {:operationId "RetrieveBalance"
                 :parameters {"account-id" "$response.body#/account-id"
                              "balance-type" "$response.body#/balance-type"
                              "currency" "$response.body#/currency"
                              "balance-status"
                              "$response.body#/balance-status"}}})
