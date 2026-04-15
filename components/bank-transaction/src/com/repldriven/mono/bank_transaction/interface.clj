(ns com.repldriven.mono.bank-transaction.interface
  (:require
    com.repldriven.mono.bank-transaction.system

    [com.repldriven.mono.bank-transaction.core :as core]
    [com.repldriven.mono.bank-transaction.store :as store]))

(defn record-transaction
  "Records a transaction and legs within a transaction.
  Does not update balances — callers must call apply-legs
  separately."
  [txn data]
  (core/record txn data))

(defn get-transactions
  "Returns transaction legs for an account, enriched
  with parent transaction type, status, and reference."
  [txn account-id]
  (store/get-transactions txn account-id))
