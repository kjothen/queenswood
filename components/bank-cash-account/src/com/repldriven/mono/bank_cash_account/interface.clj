(ns com.repldriven.mono.bank-cash-account.interface
  "Cash account lifecycle (open, close, lookup) for banks.
  Open allocates payment addresses, derives account-type from the
  party, validates the chosen product version, and seeds the
  product's balance buckets. Status transitions are driven via the
  changelog watcher; `seed-opened-account` and `seed-closed-account`
  are admin/test shortcuts that bypass it."
  (:require
    com.repldriven.mono.bank-cash-account.system

    [com.repldriven.mono.bank-cash-account.core :as core]
    [com.repldriven.mono.bank-cash-account.domain :as domain]
    [com.repldriven.mono.bank-cash-account.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn new-account
  "Open a cash account, seeding the product's balance buckets.
  Returns the account map (`:cash-account-status-opening`) or an
  anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - data: map with `:bank-id`, `:party-id`, `:product-id`,
    `:currency`, `:name`.
  - opts (optional): map; `:policies` overrides policy resolution
    for the capability and limit checks."
  ([txn data]
   (core/open-account txn data))
  ([txn data opts]
   (core/open-account txn data opts)))

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

(defn get-account-by-type
  "Return the first account matching the given org and product-type,
  or nil. Caller should expect at most one result.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-type: product-type keyword."
  [txn bank-id product-type]
  (core/get-account-by-type txn bank-id product-type))

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

(defn close-account
  "Close an account. Returns the updated account
  (`:cash-account-status-closing`) or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - data: map with `:bank-id` and `:account-id`.
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn data]
   (core/close-account txn data))
  ([txn data opts]
   (core/close-account txn data opts)))

(defn seed-opened-account
  "Test/admin shortcut: flip an account from
  `:cash-account-status-opening` to `:cash-account-status-opened`
  by writing the transition straight to the store, bypassing the
  changelog watcher that runs the transition in production. Same
  spirit as `bank-party/seed-active-party`. Delete when a
  watcher-driven test harness lands. Returns the opened account or
  an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - account-id: account id."
  [txn bank-id account-id]
  (let-nom>
    [account (core/get-account txn bank-id account-id)
     opened (domain/opened-account account)
     saved (store/save-account txn
                               opened
                               {:account-id account-id
                                :status-before (:account-status account)
                                :status-after (:account-status opened)})]
    saved))

(defn seed-closed-account
  "Test/admin shortcut: flip an account from
  `:cash-account-status-closing` to `:cash-account-status-closed`,
  bypassing the changelog watcher. Counterpart to
  `seed-opened-account`. Returns the closed account or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - account-id: account id."
  [txn bank-id account-id]
  (let-nom>
    [account (core/get-account txn bank-id account-id)
     closed (domain/closed-account account)
     saved (store/save-account txn
                               closed
                               {:account-id account-id
                                :status-before (:account-status account)
                                :status-after (:account-status closed)})]
    saved))
