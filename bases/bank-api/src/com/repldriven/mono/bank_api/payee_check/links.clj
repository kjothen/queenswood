(ns com.repldriven.mono.bank-api.payee-check.links
  "OpenAPI 3 `links` objects for payee-check responses.")

(def from-check
  "Links available on a `PayeeCheck` response."
  {"GetCheck" {:operationId "GetPayeeCheck"
               :parameters {"check-id" "$response.body#/check-id"}}})
