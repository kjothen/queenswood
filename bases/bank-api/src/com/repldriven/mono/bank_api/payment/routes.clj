(ns com.repldriven.mono.bank-api.payment.routes
  (:require
    [com.repldriven.mono.bank-api.payment.commands :as commands]
    [com.repldriven.mono.bank-api.cash-account.examples :refer
     [CashAccountNotFound]]
    [com.repldriven.mono.bank-api.payment.examples :refer
     [BalanceNotFound PaymentNotFound]]
    [com.repldriven.mono.bank-api.payment.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def routes
  [["/payments"
    {:openapi {:tags ["Payments"] :security [{"orgAuth" []}]}}
    ["/internal"
     {:post {:summary "Submit an internal payment"
             :openapi {:operationId "SubmitInternalPayment"
                       :requestBody {:required true}
                       :parameters shared.parameters/ref-idempotency-key}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "SubmitInternalPaymentRequest"]}
             :responses {200 {:body [:ref "InternalPayment"]}
                         404 (ErrorResponse [#'CashAccountNotFound
                                             #'BalanceNotFound])}
             :handler commands/submit-internal-payment}}]
    ["/internal/{payment-id}"
     {:parameters {:path {:payment-id [:ref "PaymentId"]}}}
     [""
      {:get {:summary "Retrieve an internal payment"
             :openapi {:operationId "RetrieveInternalPayment"}
             :responses {200 {:body [:ref "InternalPayment"]}
                         404 (ErrorResponse [#'PaymentNotFound])}
             :handler queries/get-internal-payment}}]]
    ["/outbound"
     {:post {:summary "Submit an outbound payment"
             :openapi {:operationId "SubmitOutboundPayment"
                       :requestBody {:required true}
                       :parameters shared.parameters/ref-idempotency-key}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "SubmitOutboundPaymentRequest"]}
             :responses {200 {:body [:ref "OutboundPayment"]}
                         404 (ErrorResponse [#'CashAccountNotFound
                                             #'BalanceNotFound])}
             :handler commands/submit-outbound-payment}}]
    ["/outbound/{payment-id}"
     {:parameters {:path {:payment-id [:ref "PaymentId"]}}}
     [""
      {:get {:summary "Retrieve an outbound payment"
             :openapi {:operationId "RetrieveOutboundPayment"}
             :responses {200 {:body [:ref "OutboundPayment"]}
                         404 (ErrorResponse [#'PaymentNotFound])}
             :handler queries/get-outbound-payment}}]]]])
