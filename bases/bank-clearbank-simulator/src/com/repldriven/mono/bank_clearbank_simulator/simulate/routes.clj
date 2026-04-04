(ns com.repldriven.mono.bank-clearbank-simulator.simulate.routes
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.simulate.handlers
     :as handlers]))

(defn routes
  [config]
  [["/simulate"
    {:openapi {:tags ["Simulate"]}}
    ["/inbound-payment"
     {:post {:summary "Simulate an inbound payment"
             :openapi {:operationId "SimulateInboundPayment"}
             :parameters {:body [:ref "InboundPaymentRequest"]}
             :responses {202 {:body [:ref "InboundPaymentResponse"]}}
             :handler (handlers/inbound-payment config)}}]]])
