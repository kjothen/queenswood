(ns com.repldriven.mono.bank-api.onboarding.components
  (:require
    [com.repldriven.mono.bank-api.onboarding.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def OnboardingRequest
  "Binds the new bank to a confirmed legal entity. `registry` defaults
  to `uk-companies-house` (the only one today); `company-number` is
  looked up against it and must be active; `bank-name` is the
  public-facing name (the console pre-fills the registered name)."
  [:map {:closed true :json-schema/example examples/OnboardingRequest}
   [:registry {:optional true} string?]
   [:company-number string?]
   [:bank-name [:ref "Name"]]])

(def OnboardingResponse
  [:map {:json-schema/example examples/OnboardingResponse}
   [:user [:ref "User"]]
   [:bank [:ref "CreateBankResponse"]]
   [:membership [:ref "Membership"]]])

(def registry (components-registry [#'OnboardingRequest #'OnboardingResponse]))
