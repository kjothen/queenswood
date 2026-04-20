(ns com.repldriven.mono.bank-clearbank-simulator.cop.routes
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.cop.handlers
     :as handlers]))

(def routes
  [["/v1/confirmation-of-payee"
    {:openapi {:tags ["CoP"]}}
    ["/outbound"
     {:post
      {:summary "Perform a Confirmation of Payee check"
       :description
       "Synchronously checks whether the supplied account
holder name matches the account. For sort code 040004,
fires a real InboundCopRequestReceived webhook and
returns the bank's response. For other sort codes, uses
trigger values in accountHolderName: COP_NOMATCH,
COP_CLOSEMATCH, COP_UNAVAILABLE."
       :openapi {:operationId "PerformOutboundCop"}
       :parameters {:body [:ref "CopOutboundRequest"]}
       :responses {200 {:body [:ref "CopOutboundResponse"]}}
       :handler (handlers/outbound-cop nil)}}]]])
