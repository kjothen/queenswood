(ns com.repldriven.queenswood.api.company-registries.routes
  (:require
    [com.repldriven.queenswood.api.company-registries.queries :as queries]
    [com.repldriven.queenswood.api.company-registries.examples :refer
     [CompanyNotFound]]
    [com.repldriven.queenswood.api.schema :refer [ErrorResponse]]))

(def routes
  [["/companies"
    {:openapi {:tags ["Onboarding"] :security [{"bearerAuth" ["user"]}]}}
    ["/{company-number}"
     {:parameters {:path {:company-number string?}}}
     [""
      {:get {:summary "Look up a company in the registry of record"
             :openapi {:operationId "LookupCompany"}
             :responses {200 {:body [:ref "Company"]}
                         404 (ErrorResponse [#'CompanyNotFound])}
             :handler queries/lookup-company}}]]]])
