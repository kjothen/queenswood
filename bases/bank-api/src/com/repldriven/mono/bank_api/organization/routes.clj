(ns com.repldriven.mono.bank-api.organization.routes
  (:require
    [com.repldriven.mono.bank-api.organization.examples :refer
     [OrganizationLimitExceeded TierNotFound]]
    [com.repldriven.mono.bank-api.organization.handlers :as handlers]
    [com.repldriven.mono.bank-api.organization.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/organizations"
    {:openapi {:tags ["Organizations"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "Retrieve organizations"
            :openapi {:operationId "RetrieveOrganizations"}
            :responses {200 {:body [:ref "OrganizationList"]}}
            :handler queries/list-organizations}
      :post {:summary "Create a new organization"
             :openapi {:operationId "CreateOrganization"
                       :requestBody {:required true}}
             :parameters {:body [:ref "CreateOrganizationRequest"]}
             :responses {201 {:body [:ref "CreateOrganizationResponse"]}
                         404 (ErrorResponse [#'TierNotFound])
                         422 (ErrorResponse [#'OrganizationLimitExceeded])}
             :handler handlers/create-organization}}]]])
