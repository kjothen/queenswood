(ns com.repldriven.queenswood.api.onboarding.routes
  (:require
    [com.repldriven.queenswood.api.onboarding.examples :refer
     [AlreadyOnboarded CompanyNotActive CompanyNotFound]]
    [com.repldriven.queenswood.api.onboarding.handlers :as handlers]

    [com.repldriven.queenswood.api.schema :refer [ErrorResponse]]))

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
