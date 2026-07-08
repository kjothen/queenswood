(ns com.repldriven.mono.bank-cash-account-query.interface
  "Read-side (query) surface for cash accounts. Loads and lists
  accounts, optionally enriching with balances and transactions. This
  is the only cash-account brick `bank-api` is allowed to require: it
  exposes no writes. State changes go through `bank-cash-account`
  (commands), which itself reuses these reads inside its own
  transactions.

  The `find-account`, `find-account-by-idempotency-key`, `count-by-org`
  and `count-by-org-product-account-type-currency` fns are read
  primitives for the write sibling's transactions; the public reads for
  API consumers are `get-account`, `get-accounts`,
  `find-account-by-product` and `get-account-by-bban`."
  (:require
    [com.repldriven.mono.bank-cash-account-query.core :as core]
    [com.repldriven.mono.bank-cash-account-query.store :as store]))

(defn get-account
  "Load a single cash account. Returns the account map or a
  `:cash-account/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - account-id: account id.
  - opts (optional): map; `:embed-balances` and
    `:embed-transactions` enrich the result."
  ([txn bank-id account-id]
   (core/get-account txn bank-id account-id))
  ([txn bank-id account-id opts]
   (core/get-account txn bank-id account-id opts)))

(defn get-accounts
  "List cash accounts for a bank. Returns
  `{:accounts [...] :before id|nil :after id|nil}` or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - opts (optional): map; `:after`, `:before`, `:limit`,
    `:embed-balances`, `:embed-transactions`."
  ([txn bank-id]
   (core/get-accounts txn bank-id))
  ([txn bank-id opts]
   (core/get-accounts txn bank-id opts)))

(defn find-account-by-product
  "Return the first CashAccount whose `(bank-id, product-id)` match,
  or nil.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id."
  [txn bank-id product-id]
  (core/find-account-by-product txn bank-id product-id))

(defn get-account-by-bban
  "Return the account matching the given BBAN, or nil.

  Args:
  - txn: FDB transaction or db handle.
  - bban: basic bank account number string."
  [txn bban]
  (core/get-account-by-bban txn bban))

(defn find-account
  "Load a single cash account by primary key without enrichment or
  rejection; returns the raw account map or nil. A read primitive for
  the write sibling's transactions (e.g. the status-transition
  watcher).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - account-id: account id."
  [txn bank-id account-id]
  (store/find-account txn bank-id account-id))

(defn find-account-by-idempotency-key
  "Return the CashAccount previously written under `idempotency-key`,
  or nil. A read primitive for the write sibling's open-account
  idempotency read-back.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - idempotency-key: the command's idempotency key."
  [txn bank-id idempotency-key]
  (store/find-account-by-idempotency-key txn bank-id idempotency-key))

(defn count-by-org
  "Count all cash accounts for a bank. A read primitive for the write
  sibling's limit checks.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id."
  [txn bank-id]
  (store/count-by-org txn bank-id))

(defn count-by-org-product-account-type-currency
  "Count cash accounts for a bank grouped by product-type,
  account-type and currency. A read primitive for the write sibling's
  limit checks.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-type: product type keyword.
  - account-type: account type keyword.
  - currency: ISO 4217 currency string."
  [txn bank-id product-type account-type currency]
  (store/count-by-org-product-account-type-currency
   txn
   bank-id
   product-type
   account-type
   currency))
