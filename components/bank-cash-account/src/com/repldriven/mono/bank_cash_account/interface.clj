(ns com.repldriven.mono.bank-cash-account.interface
  "Cash account write side: open and close for banks. Open allocates
  payment addresses, derives account-type from the party, validates the
  chosen product version, and seeds the product's balance buckets.
  Status transitions are driven via the changelog watcher;
  `seed-opened-account` and `seed-closed-account` are admin/test
  shortcuts that bypass it.

  Account numbers are retired forever on close, never recycled — the
  fountain behind `store/allocate-payment-address` is a monotonic
  counter that structurally can't re-issue a number. Closing an
  account doesn't need to inform the fountain; there's nothing to
  release.

  Reads live in `bank-cash-account-query`; this brick reuses them inside
  its own transactions. `bank-api` requires the query brick, not this
  one — state changes reach the processor as commands over the bus."
  (:require
    com.repldriven.mono.bank-cash-account.system

    [com.repldriven.mono.bank-cash-account.core :as core]
    [com.repldriven.mono.bank-cash-account.domain :as domain]
    [com.repldriven.mono.bank-cash-account.store :as store]

    [com.repldriven.mono.bank-cash-account-query.interface :as q]

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
    [account (q/get-account txn bank-id account-id)
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
    [account (q/get-account txn bank-id account-id)
     closed (domain/closed-account account)
     saved (store/save-account txn
                               closed
                               {:account-id account-id
                                :status-before (:account-status account)
                                :status-after (:account-status closed)})]
    saved))
