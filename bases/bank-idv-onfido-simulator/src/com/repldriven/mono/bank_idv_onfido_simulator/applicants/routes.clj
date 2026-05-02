(ns com.repldriven.mono.bank-idv-onfido-simulator.applicants.routes
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.applicants.handlers
     :as handlers]))

(def routes
  [["/v3.6/applicants"
    {:openapi {:tags ["Applicants"]}}
    [""
     {:post {:summary "Create an applicant"
             :openapi {:operationId "CreateApplicant"}
             :parameters {:body [:ref "CreateApplicantRequest"]}
             :responses {201 {:body [:ref "Applicant"]}}
             :handler (handlers/create-applicant nil)}}]
    ["/{id}"
     {:get {:summary "Get an applicant"
            :openapi {:operationId "GetApplicant"}
            :parameters {:path {:id string?}}
            :responses {200 {:body [:ref "Applicant"]}
                        404 {:body [:ref "ErrorResponse"]}}
            :handler (handlers/get-applicant nil)}}]]])
