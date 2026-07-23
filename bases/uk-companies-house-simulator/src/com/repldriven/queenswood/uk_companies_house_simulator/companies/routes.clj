(ns com.repldriven.queenswood.uk-companies-house-simulator.companies.routes
  (:require
    [com.repldriven.queenswood.uk-companies-house-simulator.companies.handlers
     :as handlers]))

(def routes
  [["/company/{company_number}"
    {:openapi {:tags ["Companies"]}
     :get {:summary "Get a company profile"
           :openapi {:operationId "GetCompany"}
           :parameters {:path {:company_number string?}}
           :responses {200 {:body [:ref "Company"]}
                       404 {:body [:ref "ErrorResponse"]}}
           :handler (handlers/get-company nil)}}]])
