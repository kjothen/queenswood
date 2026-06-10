(ns com.repldriven.mono.bank-payment.interface
  "Internal, inbound, and outbound payment processing. Submission
  records balance legs and persists the payment record; settlement
  events flip outbound payments to completed and post the customer
  legs for inbound payments. Hold events mark an outbound payment held
  while the scheme screens it; rejection events reverse the in-flight
  legs (1200 → debtor) and flip the payment to failed. Returns the
  payment map or an anomaly."
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

(defn submit-internal
  "Submit an internal (same-org) payment between two cash accounts.
  Verifies both accounts under the request's `:bank-id`,
  records the transaction, posts the debit/credit legs, and
  persists an InternalPayment.

  Args:
  - config: FDB handle plus :business-day-cutoff (optional).
  - data: submission map (bank-id, debtor-account-id,
    creditor-account-id, currency, amount, reference, ...).

  Returns the payment map or an anomaly. A creditor account that
  isn't in the same org returns `:cash-account/not-found`."
  [config data]
  (core/submit-internal config data))

(defn submit-outbound
  "Submit an outbound payment: verify the debtor, debit the customer
  account, credit the bank's 1200 pending-outbound GL account,
  persist the OutboundPayment as pending, and publish a
  `submit-payment` command for the scheme adapter. The bank's 1200
  account is resolved per-bank from the chart of accounts at runtime.

  Args:
  - config: FDB handle plus :bus, :schemas,
    :scheme-payment-command-channel.
  - data: submission map (bank-id, debtor-account-id,
    creditor-bban, currency, amount, reference, ...).

  Returns the payment map or an anomaly."
  [config data]
  (core/submit-outbound config data))

(defn settle-inbound
  "Process an inbound `transaction-settled` event. Looks up the
  creditor account by BBAN, dedupes by scheme-transaction-id,
  records the transaction (DEBIT the bank's 2500 suspense GL account
  / CREDIT the creditor's customer account), posts balance legs, and
  persists an InboundPayment. The 2500 suspense account is resolved
  per-bank from the chart of accounts at runtime.

  Args:
  - config: FDB handle.
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

(defn hold-outbound
  "Process an outbound `transaction-held` event by flipping the matching
  OutboundPayment from pending to held. No balance move — the money stays
  in the 1200 pending-outbound bucket while the scheme screens it. A
  payment that is not pending is left untouched.

  Args:
  - config: FDB handle.
  - data: held event payload (end-to-end-id is our payment-id).

  Returns the updated payment map or an anomaly."
  [config data]
  (events/hold-outbound config data))

(defn reject-outbound
  "Process an outbound `transaction-rejected` event. Reverses the
  in-flight payment (DEBIT the bank's 1200 pending-outbound GL account /
  CREDIT the debtor's customer account) and flips the OutboundPayment to
  failed with the scheme's cancellation code/reason. Pending and held
  payments are reversible; an already-failed payment is an idempotent
  no-op; a settled payment cannot be reversed here.

  Args:
  - config: FDB handle.
  - data: rejection event payload (end-to-end-id is our payment-id).

  Returns the updated payment map or an anomaly."
  [config data]
  (events/reject-outbound config data))

(defn hold-inbound
  "Process an inbound `transaction-held` event. Records the inbound `held`
  (creditor resolved by BBAN) with no balance move — the funds are held at
  ClearBank until released or returned. Idempotent on an existing held.

  Args:
  - config: FDB handle.
  - data: held event payload (end-to-end-id is the scheme's identifier).

  Returns the held InboundPayment map or an anomaly."
  [config data]
  (events/hold-inbound config data))

(defn return-inbound
  "Process an inbound `transaction-rejected` event. Transitions the matching
  held InboundPayment to `returned` — the funds went back to the remitter,
  so nothing posts. A no-op when there's no open held for the end-to-end-id.

  Args:
  - config: FDB handle.
  - data: rejection event payload.

  Returns the updated payment map or an anomaly."
  [config data]
  (events/return-inbound config data))
