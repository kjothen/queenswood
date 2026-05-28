(ns com.repldriven.mono.bank-api.onboarding.examples
  (:require
    [com.repldriven.mono.bank-api.bank.examples :as bank-examples]
    [com.repldriven.mono.bank-api.me.examples :as me-examples]
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def OnboardingRequest {:bank-name "Galactic Bank"})

(def OnboardingResponse
  {:user me-examples/User
   :bank (assoc bank-examples/Bank :client-secret bank-examples/ClientSecret)
   :membership me-examples/Membership})

(def AlreadyOnboarded
  {:value {:title "REJECTED"
           :type ":membership/already-exists"
           :status 409
           :detail "User already belongs to a bank"}})

(def registry
  (examples-registry [#'OnboardingRequest #'OnboardingResponse
                      #'AlreadyOnboarded]))
