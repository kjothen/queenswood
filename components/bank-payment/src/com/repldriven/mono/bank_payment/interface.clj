(ns com.repldriven.mono.bank-payment.interface
  (:require
    com.repldriven.mono.bank-payment.system

    [com.repldriven.mono.bank-payment.core :as core]
    [com.repldriven.mono.bank-payment.store :as store]))

(defn get-internal-payment
  "Loads an internal payment by payment-id. Returns the
  payment map or nil."
  [txn payment-id]
  (store/get-internal-payment txn payment-id))

(defn get-outbound-payment
  "Loads an outbound payment by payment-id. Returns the
  payment map or nil."
  [txn payment-id]
  (store/get-outbound-payment txn payment-id))

(defn submit-outbound
  "Submits an outbound payment: verifies the debtor account
  exists in the caller's org, debits the customer account,
  credits the settlement-suspense leg, persists the
  OutboundPayment as `:outbound-payment-status-pending`, and
  publishes a `submit-payment` command for the scheme adapter
  (ClearBank). Returns the payment map or anomaly.

  `config` extends the FDB handle with `:internal-account-id`
  (the platform's settlement counter-leg)."
  [config data]
  (core/submit-outbound config data))
