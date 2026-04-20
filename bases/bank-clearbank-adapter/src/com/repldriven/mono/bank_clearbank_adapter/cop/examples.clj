(ns com.repldriven.mono.bank-clearbank-adapter.cop.examples)

(def CopOutboundRequest
  {:creditor-name "Arthur Dent"
   :account {:sort-code "040004" :account-number "12345678"}
   :account-type :personal})

(def CopOutboundResponse
  {:match-result :match :actual-name nil :reason-code nil :reason nil})

(def registry
  {"CopOutboundRequest" {:value CopOutboundRequest}
   "CopOutboundResponse" {:value CopOutboundResponse}})
