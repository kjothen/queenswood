(ns com.repldriven.mono.bank-payment.interface
  "Internal, inbound, and outbound payment processing. Submission
  records balance legs and persists the payment record; settlement
  events flip outbound payments to completed and post the customer
  legs for inbound payments. Returns the payment map or an anomaly."
  (:require
    com.repldriven.mono.bank-payment.system

    [com.repldriven.mono.bank-payment.core :as core]
    [com.repldriven.mono.bank-payment.events :as events]
    [com.repldriven.mono.bank-payment.store :as store]))

(defn get-internal-payment
  "Load an internal payment by id.

  Args:
  - txn: FDB handle or open transaction.
  - payment-id: the payment's id.

  Returns the payment map or nil."
  [txn payment-id]
  (store/get-internal-payment txn payment-id))

(defn get-outbound-payment
  "Load an outbound payment by id.

  Args:
  - txn: FDB handle or open transaction.
  - payment-id: the payment's id.

  Returns the payment map or nil."
  [txn payment-id]
  (store/get-outbound-payment txn payment-id))

(defn get-inbound-payment
  "Load an inbound payment by its scheme-transaction-id (the
  unique secondary index).

  Args:
  - txn: FDB handle or open transaction.
  - scheme-transaction-id: scheme-side unique id.

  Returns the payment map or nil."
  [txn scheme-transaction-id]
  (store/get-inbound-payment txn scheme-transaction-id))

(defn submit-outbound
  "Submit an outbound payment: verify the debtor, debit the customer
  account, credit the settlement-suspense leg, persist the
  OutboundPayment as pending, and publish a `submit-payment`
  command for the scheme adapter.

  Args:
  - config: FDB handle plus :internal-account-id, :bus, :schemas,
    :scheme-payment-command-channel.
  - data: submission map (organization-id, debtor-account-id,
    creditor-bban, currency, amount, reference, ...).

  Returns the payment map or an anomaly."
  [config data]
  (core/submit-outbound config data))

(defn settle-inbound
  "Process an inbound `transaction-settled` event. Looks up the
  creditor account by BBAN, dedupes by scheme-transaction-id,
  records the transaction, posts balance legs, and persists an
  InboundPayment. Re-delivery of the same scheme-transaction-id
  returns the existing record and posts no new legs.

  Args:
  - config: FDB handle plus :internal-account-id.
  - data: settlement event payload.

  Returns the payment map or an anomaly."
  [config data]
  (events/settle-inbound config data))

(defn settle-outbound
  "Process an outbound `transaction-settled` event by flipping the
  matching OutboundPayment from pending to completed. Already-
  completed settlements are no-ops returning the existing record.

  Args:
  - config: FDB handle.
  - data: settlement event payload (end-to-end-id is our
    payment-id).

  Returns the updated payment map or an anomaly."
  [config data]
  (events/settle-outbound config data))
