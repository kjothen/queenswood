(ns com.repldriven.mono.bank-api.onboarding.routes
  (:require
    [com.repldriven.mono.bank-api.onboarding.examples :refer
     [AlreadyOnboarded CompanyNotActive CompanyNotFound]]
    [com.repldriven.mono.bank-api.onboarding.handlers :as handlers]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/onboarding"
    {:openapi {:tags ["Onboarding"] :security [{"bearerAuth" ["user"]}]}}
    ["/me"
     {:post {:summary "First-sign-in onboarding for the authenticated user"
             :openapi {:operationId "OnboardMe" :requestBody {:required true}}
             :parameters {:body [:ref "OnboardingRequest"]}
             :responses {201 {:body [:ref "OnboardingResponse"]}
                         404 (ErrorResponse [#'CompanyNotFound])
                         409 (ErrorResponse [#'AlreadyOnboarded])
                         422 (ErrorResponse [#'CompanyNotActive])}
             :handler handlers/onboard}}]]])
