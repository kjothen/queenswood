(ns com.repldriven.mono.bank-transaction.interface
  "Double-entry transactions and their legs. Records a transaction
  with its legs in a single FDB transaction, optionally applying
  legs to balances. Returns the transaction map (with `:legs`) or
  an anomaly."
  (:require
    com.repldriven.mono.bank-transaction.system

    [com.repldriven.mono.bank-transaction.core :as core]
    [com.repldriven.mono.bank-transaction.store :as store]))

(defn record-transaction
  "Record a transaction and its legs without updating balances.
  Callers must call `apply-legs` separately when balance side-
  effects are required.

  Args:
  - txn: FDB handle or open transaction.
  - data: transaction data (idempotency-key, transaction-type,
    currency, reference, legs).

  Returns the transaction map with `:legs` or an anomaly."
  [txn data]
  (core/record txn data))

(defn get-transactions
  "List transaction legs for an account, enriched with the parent
  transaction's type, status, and reference.

  Args:
  - txn: FDB handle or open transaction.
  - account-id: account whose legs to return.
  - opts: optional map with :limit and :order (`:desc` default).

  Returns a vector of leg maps or an anomaly."
  ([txn account-id]
   (store/get-transactions txn account-id))
  ([txn account-id opts]
   (store/get-transactions txn account-id opts)))
