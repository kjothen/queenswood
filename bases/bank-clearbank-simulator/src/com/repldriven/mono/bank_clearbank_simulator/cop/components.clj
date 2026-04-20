(ns com.repldriven.mono.bank-clearbank-simulator.cop.components
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.cop.examples
     :as examples]
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [components-registry]]))

(def CopAccountDetails
  [:map
   [:sortCode string?]
   [:accountNumber string?]])

(def CopOutboundRequest
  [:map
   {:json-schema/example examples/CopOutboundRequest}
   [:accountDetails [:ref "CopAccountDetails"]]
   [:accountHolderName string?]
   [:accountType [:enum "Personal" "Business"]]
   [:secondaryIdentification {:optional true} [:maybe string?]]
   [:endToEndIdentification string?]])

(def CopOutboundResponse
  [:map
   [:endToEndIdentification string?]
   [:matchResult [:enum "Match" "CloseMatch" "NoMatch" "Unavailable"]]
   [:actualName {:optional true} [:maybe string?]]
   [:reasonCode {:optional true} [:maybe string?]]
   [:reason {:optional true} [:maybe string?]]])

(def registry
  (components-registry [#'CopAccountDetails #'CopOutboundRequest
                        #'CopOutboundResponse]))
