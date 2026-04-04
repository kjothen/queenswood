(ns com.repldriven.mono.bank-clearbank-simulator.fps.routes
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.fps.handlers :as handlers]))

(defn routes
  [config]
  [["/v3/payments"
    {:openapi {:tags ["FPS"]}}
    ["/fps"
     {:post {:summary "Send Faster Payments"
             :openapi {:operationId "SendFasterPayments"}
             :parameters {:body [:ref "FpsPaymentRequest"]}
             :responses {202 {:body [:ref "FpsPaymentResponse"]}}
             :handler (handlers/payment config)}}]]])
