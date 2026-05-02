(ns com.repldriven.mono.bank-idv-onfido-simulator.checks.routes
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.checks.handlers
     :as handlers]))

(def routes
  [["/v3.6/checks"
    {:openapi {:tags ["Checks"]}}
    [""
     {:post {:summary "Create a check on an applicant"
             :openapi {:operationId "CreateCheck"}
             :parameters {:body [:ref "CreateCheckRequest"]}
             :responses {201 {:body [:ref "Check"]}
                         422 {:body [:ref "ErrorResponse"]}}
             :handler (handlers/create-check nil)}}]
    ["/{id}"
     {:get {:summary "Get a check"
            :openapi {:operationId "GetCheck"}
            :parameters {:path {:id string?}}
            :responses {200 {:body [:ref "Check"]}
                        404 {:body [:ref "ErrorResponse"]}}
            :handler (handlers/get-check nil)}}]]])
