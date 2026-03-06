(ns com.repldriven.mono.bank-api.organizations.routes
  (:require
    [com.repldriven.mono.bank-api.organizations.handlers :as handlers]))

(def routes
  [["/organizations"
    {:post {:summary "Create an organization"
            :openapi {:operationId "CreateOrganization"
                      :security [{"adminAuth" []}]}
            :parameters {:body [:ref "CreateOrganizationRequest"]}
            :responses {201 {:body [:ref "CreateOrganizationResponse"]}}
            :handler handlers/create-organization}}]])
