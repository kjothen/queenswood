(ns com.repldriven.queenswood.balance.interface
  "Balance write side: create an account's balance buckets, apply
  transaction legs to them (with policy-gated capability and limit
  checks), and maintain credit-carry for capitalisation. Buckets are
  keyed by `(account-id, balance-type, currency, balance-status)`.

  Reads (lookup, listing with posted/available totals, trial-balance)
  live in `bank-balance-query`, which this brick reuses inside its own
  transactions. These writes are called by other bricks' processors
  (cash-account open, payment/interest/transaction posting) inside their
  FDB transactions, not as standalone commands."
  (:require
    [com.repldriven.queenswood.balance.core :as core]))

(defn new-balances
  "Create multiple balances in a single transaction; short-circuits
  on the first anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - data: collection of balance creation maps, each with `:account-id`,
    `:product-type`, `:balance-type`, `:balance-status`, `:currency`.
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn data]
   (core/new-balances txn data))
  ([txn data opts]
   (core/new-balances txn data opts)))

(defn apply-legs
  "Apply each leg to its target balance (with the
  `:balance-action-apply` capability check) and run the computed
  `:available` limit check per affected account. Returns nil on
  success or an anomaly. `transaction-type` scopes which limits
  fire via the limit's `transaction-type` filter.

  Args:
  - txn: FDB transaction or db handle.
  - legs: collection of leg maps; each carries `:account-id`,
    `:balance-type`, `:balance-status`, `:side`, `:amount`.
  - transaction-type: transaction-type keyword (e.g.
    `:transaction-type-internal-transfer`).
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn legs transaction-type]
   (core/apply-legs txn legs transaction-type))
  ([txn legs transaction-type opts]
   (core/apply-legs txn legs transaction-type opts)))

(defn set-carry
  "Update `:credit-carry` on the balance identified by the
  composite primary key. Rejects if the balance is missing.
  Returns the updated balance or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - account-id: owning account id.
  - balance-type: balance-type keyword.
  - currency: ISO 4217 currency string.
  - balance-status: balance-status keyword.
  - carry: new credit-carry value."
  [txn account-id balance-type currency balance-status carry]
  (core/set-carry txn
                  account-id
                  balance-type
                  currency
                  balance-status
                  carry))
