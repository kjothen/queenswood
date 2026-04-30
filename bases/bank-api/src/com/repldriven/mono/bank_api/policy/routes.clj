(ns com.repldriven.mono.bank-api.policy.routes
  (:require
    [com.repldriven.mono.bank-api.policy.examples :refer [PolicyNotFound]]
    [com.repldriven.mono.bank-api.policy.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/policies"
    {:openapi {:tags ["Policies"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "List all policies"
            :openapi {:operationId "ListPolicies"}
            :responses {200 {:body [:ref "PolicyList"]}}
            :handler queries/list-policies}}]
    ["/{policy-id}"
     {:parameters {:path {:policy-id [:ref "PolicyId"]}}}
     [""
      {:get {:summary "Get a policy by id"
             :openapi {:operationId "GetPolicy"}
             :responses {200 {:body [:ref "Policy"]}
                         404 (ErrorResponse [#'PolicyNotFound])}
             :handler queries/get-policy}}]]]])
