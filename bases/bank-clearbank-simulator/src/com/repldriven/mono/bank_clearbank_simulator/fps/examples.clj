(ns com.repldriven.mono.bank-clearbank-simulator.fps.examples
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [examples-registry]]))

(def FpsPaymentRequest
  {:paymentInstructions
   [{:paymentInstructionIdentification "instr-001"
     :paymentTypeCode "SIP"
     :debtor {:legalEntityIdentifier "LEI123" :address "1 Example St, London"}
     :debtorAccount {:identification
                     {:other {:identification "12345678"
                              :schemeName {:proprietary "SortCodeAccountNumber"}
                              :issuer "123456"}}}
     :creditTransfers
     [{:paymentIdentification {:instructionIdentification "ct-001"
                               :endToEndIdentification "e2e-001"}
       :amount {:instructedAmount 100.00 :currency "GBP"}
       :creditor {:name "Arthur Dent"}
       :creditorAccount {:identification {:other {:identification "87654321"
                                                  :schemeName
                                                  {:proprietary
                                                   "SortCodeAccountNumber"}
                                                  :issuer "654321"}}}
       :remittanceInformation {:unstructured {:additionalReferenceInformation
                                              {:reference "Invoice 123"}}}}]}]})

(def FpsPaymentResponse
  {:transactions [{:endToEndIdentification "e2e-001" :response "Accepted"}]
   :halLinks [{:name "self" :href "/v3/payments/fps" :templated false}]})

(def registry (examples-registry [#'FpsPaymentRequest #'FpsPaymentResponse]))
