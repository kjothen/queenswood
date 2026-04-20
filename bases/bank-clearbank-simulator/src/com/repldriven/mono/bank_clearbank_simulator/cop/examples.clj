(ns com.repldriven.mono.bank-clearbank-simulator.cop.examples
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [examples-registry]]))

(def CopOutboundRequest
  {:accountDetails {:sortCode "040004" :accountNumber "12345678"}
   :accountHolderName "Arthur Dent"
   :accountType "Personal"
   :endToEndIdentification "cop-e2e-001"})

(def CopOutboundResponseMatch
  {:value {:endToEndIdentification "cop-e2e-001" :matchResult "Match"}})

(def CopOutboundResponseCloseMatch
  {:value {:endToEndIdentification "cop-e2e-001"
           :matchResult "CloseMatch"
           :actualName "Jane A Doe"
           :reasonCode "PANM"
           :reason "Partial name match"}})

(def CopOutboundResponseNoMatch
  {:value {:endToEndIdentification "cop-e2e-001"
           :matchResult "NoMatch"
           :reasonCode "ANNM"
           :reason "Account name does not match"}})

(def CopOutboundResponseUnavailable
  {:value {:endToEndIdentification "cop-e2e-001"
           :matchResult "Unavailable"
           :reasonCode "ACNS"
           :reason "Account does not support CoP"}})

(def registry
  (examples-registry [#'CopOutboundResponseMatch #'CopOutboundResponseCloseMatch
                      #'CopOutboundResponseNoMatch
                      #'CopOutboundResponseUnavailable]))
