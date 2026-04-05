(ns com.repldriven.mono.bank-api.payment.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def PaymentNotFound
  {:value {:title "REJECTED"
           :type "payment/not-found"
           :status 404
           :detail "Payment not found"}})

(def registry (examples-registry [#'PaymentNotFound]))

(def SubmitInternalPaymentRequest
  {:idempotency-key "idem-001"
   :debtor-account-id "acc_01JMABC"
   :creditor-account-id "acc_02JMABC"
   :currency "GBP"
   :amount 1000
   :reference "Internal transfer"})

(def InternalPayment
  {:payment-id "pmt_01JMABC"
   :idempotency-key "idem-001"
   :debtor-account-id "acc_01JMABC"
   :creditor-account-id "acc_02JMABC"
   :currency "GBP"
   :amount 1000
   :transaction-id "txn_01JMABC"
   :reference "Internal transfer"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def SubmitOutboundPaymentRequest
  {:idempotency-key "idem-002"
   :debtor-account-id "acc_01JMABC"
   :creditor-bban "04000412345678"
   :creditor-name "Jane Doe"
   :currency "GBP"
   :amount 500
   :scheme "FPS"
   :reference "Invoice 123"})

(def OutboundPayment
  {:payment-id "pmt_02JMABC"
   :idempotency-key "idem-002"
   :end-to-end-id "e2e-abc-123"
   :scheme "FPS"
   :debtor-account-id "acc_01JMABC"
   :creditor-bban "04000412345678"
   :creditor-name "Jane Doe"
   :currency "GBP"
   :amount 500
   :payment-status :outbound-payment-status-pending
   :transaction-id "txn_02JMABC"
   :reference "Invoice 123"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})
