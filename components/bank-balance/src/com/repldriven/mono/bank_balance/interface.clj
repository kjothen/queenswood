(ns com.repldriven.mono.bank-balance.interface
  (:require
    [com.repldriven.mono.bank-balance.core :as core]
    [com.repldriven.mono.bank-balance.store :as store]))

(defn new-balance
  "Creates a new balance. Rejects if the balance already
  exists. Returns the balance or anomaly."
  [txn data]
  (core/new-balance txn data))

(defn new-balances
  "Creates multiple balances in a single transaction.
  Short-circuits on the first anomaly. Returns the
  created balances or anomaly."
  [txn data]
  (core/new-balances txn data))

(defn get-balance
  "Loads a balance by account-id, balance-type, currency,
  and balance-status. Returns the balance, nil if not
  found, or anomaly."
  [txn account-id balance-type currency balance-status]
  (store/get-balance txn
                     account-id
                     balance-type
                     currency
                     balance-status))

(defn get-balances
  "Lists balances for an account. Returns
  {:balances [...] :posted-balance {...}
   :available-balance {...}} or anomaly."
  [txn account-id]
  (core/get-balances txn account-id))

(defn apply-legs
  "Applies all legs to balances within a transaction.
  Returns nil or anomaly."
  [txn legs]
  (core/apply-legs txn legs))

(defn set-carry
  "Updates the :credit-carry on the balance identified by
  the composite PK. Returns the updated balance or
  anomaly."
  [txn account-id balance-type currency balance-status carry]
  (core/set-carry txn
                  account-id
                  balance-type
                  currency
                  balance-status
                  carry))
