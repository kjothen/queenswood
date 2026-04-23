(ns com.repldriven.mono.bank-api.payment.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def PaymentNotFound
  {:value {:title "REJECTED"
           :type "payment/not-found"
           :status 404
           :detail "Payment not found"}})

(def BalanceNotFound
  {:value {:title "REJECTED"
           :type ":balance/not-found"
           :status 404
           :detail "Balance not found"}})

(def registry (examples-registry [#'PaymentNotFound #'BalanceNotFound]))

(def PaymentId "pmt.01kprbmgcj35ptc8npmybhh4s5")

(def SubmitInternalPaymentRequest
  {:debtor-account-id "acc.01kprbmgcj35ptc8npmybhh4s8"
   :creditor-account-id "acc.01kprbmgcj35ptc8npmybhh4s9"
   :currency "GBP"
   :amount 1000
   :reference "Internal transfer"})

(def InternalPayment
  {:payment-id PaymentId
   :debtor-account-id "acc.01kprbmgcj35ptc8npmybhh4s8"
   :creditor-account-id "acc.01kprbmgcj35ptc8npmybhh4s9"
   :currency "GBP"
   :amount 1000
   :transaction-id "txn.01kprbmgcj35ptc8npmybhh4sb"
   :reference "Internal transfer"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def SubmitOutboundPaymentRequest
  {:debtor-account-id "acc.01kprbmgcj35ptc8npmybhh4s8"
   :creditor-bban "04000412345678"
   :creditor-name "Arthur Dent"
   :currency "GBP"
   :amount 500
   :scheme "FPS"
   :reference "Invoice 123"})

(def OutboundPayment
  {:payment-id "pmt.01kprbmgcj35ptc8npmybhh4s6"
   :scheme "FPS"
   :debtor-account-id "acc.01kprbmgcj35ptc8npmybhh4s8"
   :creditor-bban "04000412345678"
   :creditor-name "Arthur Dent"
   :currency "GBP"
   :amount 500
   :payment-status :outbound-payment-status-pending
   :transaction-id "txn.01kprbmgcj35ptc8npmybhh4sb"
   :reference "Invoice 123"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})
