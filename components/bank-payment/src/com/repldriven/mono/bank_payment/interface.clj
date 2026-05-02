(ns com.repldriven.mono.bank-payment.interface
  (:require
    com.repldriven.mono.bank-payment.system

    [com.repldriven.mono.bank-payment.core :as core]
    [com.repldriven.mono.bank-payment.events :as events]
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

(defn get-inbound-payment
  "Loads an inbound payment by `:scheme-transaction-id` (the
  unique secondary index). Returns the payment map or nil."
  [txn scheme-transaction-id]
  (store/get-inbound-payment txn scheme-transaction-id))

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

(defn settle-inbound
  "Processes an inbound `transaction-settled` event. Looks up the
  creditor account by BBAN, dedupes by `:scheme-transaction-id`,
  records the transaction, posts the balance legs (customer
  default credit, settlement suspense debit), and persists an
  InboundPayment. Returns the payment map or anomaly.

  Re-delivery of the same `:scheme-transaction-id` returns the
  existing record and posts no new legs.

  `config` shape matches `submit-outbound` — extend the FDB
  handle with `:internal-account-id`."
  [config data]
  (events/settle-inbound config data))

(defn settle-outbound
  "Processes an outbound `transaction-settled` event. Looks up
  the OutboundPayment by `:end-to-end-id` (= our payment-id) and
  flips its status from `:pending` to `:completed`. Returns the
  updated payment or anomaly. Already-completed settlements are
  no-ops returning the existing record."
  [config data]
  (events/settle-outbound config data))
