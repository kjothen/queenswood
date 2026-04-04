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

(def registry
  (examples-registry [#'InboundPaymentRequest #'InboundPaymentResponse]))
