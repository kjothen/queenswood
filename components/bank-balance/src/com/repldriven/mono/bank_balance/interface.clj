(ns com.repldriven.mono.bank-balance.interface
  "Per-account balance buckets keyed by `(account-id, balance-type,
  currency, balance-status)`. Provides creation, lookup, listing
  with derived posted/available totals, application of transaction
  legs with policy-gated capability and limit checks, and
  credit-carry maintenance for capitalisation."
  (:require
    [com.repldriven.mono.bank-balance.core :as core]
    [com.repldriven.mono.bank-balance.store :as store]))

(defn new-balance
  "Create a single balance bucket. Rejects if a balance with the
  same composite key already exists. Returns the balance or an
  anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - data: map with `:account-id`, `:product-type`, `:balance-type`,
    `:balance-status`, `:currency`.
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn data]
   (core/new-balance txn data))
  ([txn data opts]
   (core/new-balance txn data opts)))

(defn new-balances
  "Create multiple balances in a single transaction; short-circuits
  on the first anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - data: collection of balance creation maps (see `new-balance`).
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn data]
   (core/new-balances txn data))
  ([txn data opts]
   (core/new-balances txn data opts)))

(defn get-balance
  "Look up a single balance by its composite primary key. Returns
  the balance map or a `:balance/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - account-id: owning account id.
  - balance-type: balance-type keyword.
  - currency: ISO 4217 currency string.
  - balance-status: balance-status keyword."
  [txn account-id balance-type currency balance-status]
  (store/get-balance txn
                     account-id
                     balance-type
                     currency
                     balance-status))

(defn get-balances
  "List all balances for an account, enriched with derived
  posted-balance and available-balance totals. Returns
  `{:balances [...] :posted-balance {...} :available-balance {...}}`
  or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - account-id: owning account id."
  [txn account-id]
  (core/get-balances txn account-id))

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
