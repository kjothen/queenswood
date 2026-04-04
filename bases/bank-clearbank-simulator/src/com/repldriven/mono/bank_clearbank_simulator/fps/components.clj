(ns com.repldriven.mono.bank-clearbank-simulator.fps.components
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.fps.examples
     :as examples]
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [components-registry]]))

(def AccountIdentification
  [:map
   [:iban {:optional true} [:maybe string?]]
   [:other {:optional true}
    [:maybe
     [:map
      [:identification string?]
      [:schemeName
       [:map
        [:code {:optional true} [:maybe string?]]
        [:proprietary {:optional true}
         [:maybe string?]]]]
      [:issuer {:optional true} [:maybe string?]]]]]])

(def CreditTransfer
  [:map
   [:paymentIdentification
    [:map
     [:instructionIdentification string?]
     [:endToEndIdentification string?]]]
   [:amount
    [:map
     [:instructedAmount number?]
     [:currency string?]]]
   [:creditor
    [:map
     [:name string?]
     [:legalEntityIdentifier {:optional true}
      [:maybe string?]]]]
   [:creditorAccount
    [:map
     [:identification [:ref "AccountIdentification"]]]]
   [:remittanceInformation {:optional true}
    [:maybe
     [:map
      [:structured {:optional true}
       [:maybe
        [:map
         [:creditorReferenceInformation
          [:map
           [:reference string?]]]]]]
      [:unstructured {:optional true}
       [:maybe
        [:map
         [:additionalReferenceInformation
          [:map
           [:reference string?]]]]]]]]]
   [:enforceSendToScheme {:optional true} [:maybe boolean?]]])

(def PaymentInstruction
  [:map
   [:paymentInstructionIdentification string?]
   [:paymentTypeCode [:enum "SIP" "SOP" "FDP"]]
   [:debtor {:optional true}
    [:maybe
     [:map
      [:legalEntityIdentifier {:optional true}
       [:maybe string?]]
      [:address {:optional true} [:maybe string?]]]]]
   [:debtorAccount
    [:map
     [:identification [:ref "AccountIdentification"]]]]
   [:creditTransfers [:vector [:ref "CreditTransfer"]]]])

(def FpsPaymentRequest
  [:map
   {:json-schema/example examples/FpsPaymentRequest}
   [:paymentInstructions
    [:vector {:min 1 :max 10} [:ref "PaymentInstruction"]]]])

(def FpsTransactionResponse
  [:map
   [:endToEndIdentification string?]
   [:response string?]])

(def HalLink
  [:map
   [:name string?]
   [:href string?]
   [:templated {:optional true} [:maybe boolean?]]])

(def FpsPaymentResponse
  [:map
   {:json-schema/example examples/FpsPaymentResponse}
   [:transactions [:vector [:ref "FpsTransactionResponse"]]]
   [:halLinks {:optional true}
    [:maybe [:vector [:ref "HalLink"]]]]])

(def registry
  (components-registry [#'AccountIdentification #'CreditTransfer
                        #'PaymentInstruction #'FpsPaymentRequest
                        #'FpsTransactionResponse #'HalLink
                        #'FpsPaymentResponse]))
