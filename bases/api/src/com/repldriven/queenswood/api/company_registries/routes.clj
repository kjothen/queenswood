(ns com.repldriven.queenswood.api.company-registries.routes
  (:require
    [com.repldriven.queenswood.api.company-registries.queries :as queries]
    [com.repldriven.queenswood.api.company-registries.examples :refer
     [CompanyNotFound RegistryNotFound]]
    [com.repldriven.queenswood.api.schema :refer [ErrorResponse]]))

(def routes
  [["/company-registries"
    {:openapi {:tags ["Onboarding"] :security [{"bearerAuth" ["user"]}]}}
    ["/{registry-id}/companies/{company-number}"
     {:parameters {:path {:registry-id string? :company-number string?}}}
     [""
      {:get {:summary "Look up a company in a registry"
             :openapi {:operationId "LookupCompany"}
             :responses {200 {:body [:ref "Company"]}
                         404 (ErrorResponse [#'CompanyNotFound
                                             #'RegistryNotFound])}
             :handler queries/lookup-company}}]]]])
