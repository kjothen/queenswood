(ns com.repldriven.mono.bank-balance.interface
  (:require
    [com.repldriven.mono.bank-balance.core :as core]
    [com.repldriven.mono.bank-balance.store :as store]))

(defn new-balance
  "Creates a new balance. Rejects if the balance already
  exists. opts supports `:policies` to override policy
  resolution."
  ([txn data]
   (core/new-balance txn data))
  ([txn data opts]
   (core/new-balance txn data opts)))

(defn new-balances
  "Creates multiple balances in a single transaction.
  Short-circuits on the first anomaly. opts supports
  `:policies` to override policy resolution."
  ([txn data]
   (core/new-balances txn data))
  ([txn data opts]
   (core/new-balances txn data opts)))

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
  `transaction-type` is required (e.g.
  `:transaction-type-internal-transfer`) and is used by the
  computed `:available` limit's `transaction-type` filter to
  scope which limits fire. opts supports `:policies` to
  override policy resolution."
  ([txn legs transaction-type]
   (core/apply-legs txn legs transaction-type))
  ([txn legs transaction-type opts]
   (core/apply-legs txn legs transaction-type opts)))

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
