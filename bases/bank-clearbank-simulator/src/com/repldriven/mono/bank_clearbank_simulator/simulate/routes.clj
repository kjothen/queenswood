(ns com.repldriven.mono.bank-clearbank-simulator.simulate.routes
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.simulate.handlers
     :as handlers]))

(def routes
  [["/simulate"
    {:openapi {:tags ["Simulate"]}}
    ["/inbound-payment"
     {:post {:summary "Simulate an inbound payment"
             :openapi {:operationId "SimulateInboundPayment"}
             :parameters {:body [:ref "InboundPaymentRequest"]}
             :responses {202 {:body [:ref "InboundPaymentResponse"]}}
             :handler (handlers/inbound-payment nil)}}]
    ["/inbound-cop-request"
     {:post
      {:summary "Simulate an inbound CoP request"
       :description
       "Triggers an InboundCopRequestReceived webhook,
asking the adapter to confirm (or deny) that
accountHolderName matches one of its accounts."
       :openapi {:operationId "SimulateInboundCopRequest"}
       :parameters {:body [:ref "SimulateInboundCopRequest"]}
       :responses {202 {:body [:ref "SimulateInboundCopResponse"]}}
       :handler (handlers/inbound-cop-request nil)}}]]])
