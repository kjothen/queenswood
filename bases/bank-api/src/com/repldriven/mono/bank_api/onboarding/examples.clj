(ns com.repldriven.mono.bank-api.onboarding.examples
  (:require
    [com.repldriven.mono.bank-api.me.examples :as me-examples]
    [com.repldriven.mono.bank-api.organization.examples :as
     organization-examples]
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def OnboardingRequest {:organization-name "Galactic Bank"})

(def OnboardingResponse
  {:user me-examples/User
   :organization (assoc organization-examples/Organization
                        :client-secret
                        organization-examples/ClientSecret)
   :membership me-examples/Membership})

(def AlreadyOnboarded
  {:value {:title "REJECTED"
           :type ":membership/already-exists"
           :status 409
           :detail "User already belongs to an organization"}})

(def registry
  (examples-registry [#'OnboardingRequest #'OnboardingResponse
                      #'AlreadyOnboarded]))
