(ns com.repldriven.mono.bank-clearbank-simulator.simulate.examples
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.schema
     :refer [examples-registry]]))

(def InboundPaymentRequest
  {:bban "04000412345678"
   :amount 100.00
   :currency "GBP"
   :reference "Test inbound payment"})

(def InboundPaymentResponse {:endToEndIdentification "e2e-abc-123"})

(def SimulateInboundCopRequest
  {:accountDetails {:sortCode "040004" :accountNumber "12345678"}
   :accountHolderName "Ford Perfect"
   :accountType "Personal"
   :requestingInstitution "Galactic Bank Limited"})

(def SimulateInboundCopResponse {:requestId "cop-req-abc-123"})

(def registry
  (examples-registry [#'InboundPaymentRequest #'InboundPaymentResponse
                      #'SimulateInboundCopRequest
                      #'SimulateInboundCopResponse]))
