(ns com.repldriven.mono.bank-clearbank-adapter.cop.components
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.cop.examples
     :as examples]))

(def CopAccountType
  [:enum {:json-schema/example "personal"}
   :account-type-personal :account-type-business])

(def CopMatchResult
  [:enum {:json-schema/example "match"}
   :match-result-match :match-result-close-match
   :match-result-no-match :match-result-unavailable])

(def CopAccount
  [:map
   {:json-schema/example {:sort-code "040004" :account-number "12345678"}}
   [:sort-code string?]
   [:account-number string?]])

(def CopOutboundRequest
  [:map
   {:json-schema/example examples/CopOutboundRequest}
   [:creditor-name string?]
   [:account [:ref "CopAccount"]]
   [:account-type [:ref "CopAccountType"]]])

(def CopOutboundResponse
  [:map
   {:json-schema/example examples/CopOutboundResponse}
   [:match-result [:ref "CopMatchResult"]]
   [:actual-name {:optional true} [:maybe string?]]
   [:reason-code {:optional true} [:maybe string?]]
   [:reason {:optional true} [:maybe string?]]])

(def registry
  {"CopAccountType" CopAccountType
   "CopMatchResult" CopMatchResult
   "CopAccount" CopAccount
   "CopOutboundRequest" CopOutboundRequest
   "CopOutboundResponse" CopOutboundResponse})
