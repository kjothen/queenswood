(ns com.repldriven.mono.bank-api.onboarding.components
  (:require
    [com.repldriven.mono.bank-api.onboarding.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def OnboardingRequest
  [:map {:closed true :json-schema/example examples/OnboardingRequest}
   [:organization-name [:ref "Name"]]])

(def OnboardingResponse
  [:map {:json-schema/example examples/OnboardingResponse}
   [:user [:ref "User"]]
   [:organization [:ref "CreateOrganizationResponse"]]
   [:membership [:ref "Membership"]]])

(def registry (components-registry [#'OnboardingRequest #'OnboardingResponse]))
