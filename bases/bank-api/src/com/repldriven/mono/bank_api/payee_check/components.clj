(ns com.repldriven.mono.bank-api.payee-check.components
  (:require
    [com.repldriven.mono.bank-api.payee-check.coercion :as coercion]
    [com.repldriven.mono.bank-api.payee-check.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def PayeeCheckAccountType
  (coercion/account-type-enum-schema {:json-schema/example "personal"}))

(def MatchResult
  (coercion/match-result-enum-schema {:json-schema/example "match"}))

(def PayeeCheckAccount
  [:map
   [:sort-code string?]
   [:account-number string?]])

(def PayeeCheckRequest
  [:map
   {:json-schema/example examples/PayeeCheckRequest}
   [:creditor-name string?]
   [:account [:ref "PayeeCheckAccount"]]
   [:account-type [:ref "PayeeCheckAccountType"]]])

(def PayeeCheckResult
  [:map
   [:match-result [:ref "MatchResult"]]
   [:actual-name {:optional true} [:maybe string?]]
   [:reason-code {:optional true} [:maybe string?]]
   [:reason {:optional true} [:maybe string?]]])

(def PayeeCheck
  [:map
   {:json-schema/example examples/PayeeCheck}
   [:check-id string?]
   [:request [:ref "PayeeCheckRequest"]]
   [:result [:ref "PayeeCheckResult"]]
   [:created-at string?]
   [:expires-at string?]])

(def PayeeCheckLinks
  [:map
   [:next {:optional true} string?]
   [:prev {:optional true} string?]])

(def PayeeCheckList
  [:map
   {:json-schema/example examples/PayeeCheckList}
   [:items [:vector [:ref "PayeeCheck"]]]
   [:links {:optional true} [:ref "PayeeCheckLinks"]]])

(def registry
  (components-registry [#'PayeeCheckAccountType #'MatchResult
                        #'PayeeCheckAccount #'PayeeCheckRequest
                        #'PayeeCheckResult #'PayeeCheck #'PayeeCheckLinks
                        #'PayeeCheckList]))
