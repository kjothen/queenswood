(ns com.repldriven.queenswood.balance-query.interface
  "Read-side (query) surface for balances: load one balance, list an
  account's balances (enriched with posted/available totals), and the
  pure `trial-balance` aggregation. This is the only balance brick
  `bank-api` (and other readers) may require — it exposes no writes.
  Balance mutation (`apply-legs`, `new-balances`, `set-carry`) lives in
  `bank-balance`, which reuses these reads inside its own transactions.

  `find-balance` and `list-balances` are read primitives for the write
  sibling's transactions; `get-balance` / `get-balances` are the public
  reads."
  (:require
    [com.repldriven.queenswood.balance-query.core :as core]
    [com.repldriven.queenswood.balance-query.store :as store]

    [com.repldriven.queenswood.balance-domain.interface :as domain]))

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
  (store/get-balance txn account-id balance-type currency balance-status))

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

(defn list-balances
  "List an account's raw balance buckets (a vector, unenriched). A read
  primitive for the write sibling's apply-legs computation.

  Args:
  - txn: FDB transaction or db handle.
  - account-id: owning account id."
  [txn account-id]
  (store/get-balances txn account-id))

(defn find-balance
  "Load a single balance by composite key without rejecting when
  absent; returns the balance map or nil. A read primitive for the
  write sibling's transactions.

  Args:
  - txn: FDB transaction or db handle.
  - account-id: owning account id.
  - balance-type: balance-type keyword.
  - currency: ISO 4217 currency string.
  - balance-status: balance-status keyword."
  [txn account-id balance-type currency balance-status]
  (store/find-balance txn account-id balance-type currency balance-status))

(defn trial-balance
  "Aggregate account-level posted balances into a per-currency trial
  balance — `[{:currency :debit :credit :accounts}]`, one block per
  currency, Sigma-debit equal to Sigma-credit when the currency's books
  balance.

  Args:
  - entries: collection of `{:currency :normal-side :value}`, where
    `:normal-side` is `:debit`/`:credit` (the account's normal side) and
    `:value` is the credit-positive posted net."
  [entries]
  (domain/trial-balance entries))
