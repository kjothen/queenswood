(ns com.repldriven.queenswood.bank-query.interface
  "Read-side (query) surface for banks: load one flat, look up by sort
  code, list rich, and the rich single-bank view (party + accounts with
  balances + client-id). This is the only bank brick `bank-api` (and
  other readers) may require — it exposes no writes. Bank provisioning
  lives in `bank-bank` (commands), reached over the bus."
  (:require
    [com.repldriven.queenswood.bank-query.core :as core]
    [com.repldriven.queenswood.bank-query.store :as store]))

(defn get-bank
  "Load a flat bank map by id. Returns the bank or a
  `:bank/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: bank id."
  [txn bank-id]
  (store/get-bank txn bank-id))

(defn get-bank-by-sort-code
  "Load a flat bank map by its sort code (the first 6 digits of its
  accounts' BBANs). Returns the bank, or nil if no bank owns that sort
  code.

  Args:
  - txn: FDB transaction or db handle.
  - sort-code: 6-digit sort code string."
  [txn sort-code]
  (store/get-bank-by-sort-code txn sort-code))

(defn get-bank-view
  "Load a bank by id enriched with its party, its accounts (with
  balances and GL codes), and `:client-id`. Returns the rich bank map,
  a `:bank/not-found` rejection, or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: bank id."
  [txn bank-id]
  (core/get-bank-view txn bank-id))

(defn get-banks
  "List banks enriched with party and accounts (with balances).
  Returns a vector of rich bank maps or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - opts (optional): map; `:limit` and `:order` (default `:desc`)."
  ([txn] (core/get-banks txn))
  ([txn opts] (core/get-banks txn opts)))
