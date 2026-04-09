(ns com.repldriven.mono.bank-api.restriction.routes
  (:require
    [com.repldriven.mono.bank-api.restriction.queries
     :as queries]))

(defn- response
  [schema-ref example-name]
  {:content
   {"application/json"
    {:schema [:ref schema-ref]
     :examples
     {example-name
      {"$ref" (str "#/components/examples/" example-name)}}}}})

(def routes
  [["/restrictions"
    {:openapi {:tags ["Restrictions"]}}
    ["/policies"
     ["/organizations"
      {:get {:summary "Retrieve organization policies"
             :openapi {:operationId "RetrieveOrganizationPolicies"}
             :responses {200 (response "PolicyList" "OrganizationPolicies")}
             :handler queries/get-organization-policies}}]
     ["/cash-account-products"
      {:get {:summary "Retrieve cash account product policies"
             :openapi {:operationId "RetrieveCashAccountProductPolicies"}
             :responses {200 (response "PolicyList"
                                       "CashAccountProductPolicies")}
             :handler queries/get-cash-account-product-policies}}]
     ["/cash-accounts"
      {:get {:summary "Retrieve cash account policies"
             :openapi {:operationId "RetrieveCashAccountPolicies"}
             :responses {200 (response "PolicyList" "CashAccountPolicies")}
             :handler queries/get-cash-account-policies}}]
     ["/parties"
      {:get {:summary "Retrieve party policies"
             :openapi {:operationId "RetrievePartyPolicies"}
             :responses {200 (response "PolicyList" "PartyPolicies")}
             :handler queries/get-party-policies}}]]
    ["/limits"
     ["/organizations"
      {:get {:summary "Retrieve organization limits"
             :openapi {:operationId "RetrieveOrganizationLimits"}
             :responses {200 (response "LimitList" "OrganizationLimits")}
             :handler queries/get-organization-limits}}]
     ["/cash-account-products"
      {:get {:summary "Retrieve cash account product limits"
             :openapi {:operationId "RetrieveCashAccountProductLimits"}
             :responses {200 (response "LimitList" "CashAccountProductLimits")}
             :handler queries/get-cash-account-product-limits}}]
     ["/cash-accounts"
      {:get {:summary "Retrieve cash account limits"
             :openapi {:operationId "RetrieveCashAccountLimits"}
             :responses {200 (response "LimitList" "CashAccountLimits")}
             :handler queries/get-cash-account-limits}}]
     ["/parties"
      {:get {:summary "Retrieve party limits"
             :openapi {:operationId "RetrievePartyLimits"}
             :responses {200 (response "LimitList" "PartyLimits")}
             :handler queries/get-party-limits}}]]]])
