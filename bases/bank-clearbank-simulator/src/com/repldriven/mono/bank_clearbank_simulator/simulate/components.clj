(ns com.repldriven.mono.bank-clearbank-simulator.simulate.components
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.simulate.examples
     :as examples]
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [components-registry]]))

(def InboundPaymentRequest
  [:map
   {:json-schema/example examples/InboundPaymentRequest}
   [:bban string?]
   [:amount number?]
   [:currency string?]
   [:reference {:optional true} [:maybe string?]]])

(def InboundPaymentResponse
  [:map
   {:json-schema/example examples/InboundPaymentResponse}
   [:endToEndIdentification string?]])

(def SimulateInboundCopRequest
  [:map
   {:json-schema/example examples/SimulateInboundCopRequest}
   [:accountDetails
    [:map
     [:sortCode string?]
     [:accountNumber string?]]]
   [:accountHolderName string?]
   [:accountType [:enum "Personal" "Business"]]
   [:requestingInstitution string?]])

(def SimulateInboundCopResponse
  [:map
   {:json-schema/example examples/SimulateInboundCopResponse}
   [:requestId string?]])

(def registry
  (components-registry [#'InboundPaymentRequest #'InboundPaymentResponse
                        #'SimulateInboundCopRequest
                        #'SimulateInboundCopResponse]))
