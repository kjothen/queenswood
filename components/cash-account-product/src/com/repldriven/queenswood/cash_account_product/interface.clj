(ns com.repldriven.queenswood.cash-account-product.interface
  "Cash-account product write side: the per-bank product lifecycle —
  create a product (initial draft), open/update/discard a draft version,
  and publish it. A product is versioned through draft, published and
  discarded states; the published version effective today backs
  newly-opened accounts. `new-template` seeds the platform templates
  products are created from.

  Reads live in `bank-cash-account-product-query`; this brick reuses them
  inside its own transactions. `bank-api` requires the query brick, not
  this one — lifecycle changes reach the processor as commands over the
  bus."
  (:require
    com.repldriven.queenswood.cash-account-product.system

    [com.repldriven.queenswood.cash-account-product.core :as core]))

(defn new-product
  "Create a new product as an initial draft v1. Returns the
  draft version map or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - data: version fields:
    - `:name` — product display name (required).
    - `:template-id` — the platform template this product is created
      from (required). product-type and the derived instrument fields
      (balance-sheet-side, balance buckets, payment-address schemes,
      ISO 20022 type) are snapshotted from it at creation, so later
      template edits never change an existing product.
    - `:currency` — ISO 4217 string, single currency per product.
    - `:interest-rate-bps` — optional; defaults to 0.
    - `:effective-from` — epoch-day (required), the date the version
      becomes active.
    - `:effective-to` — optional epoch-day; open-ended when absent.
    - `:idempotency-key` — optional; the command envelope id, unique per
      bank so a redelivered create reads the original product back.
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn bank-id data]
   (core/new-product txn bank-id data))
  ([txn bank-id data opts]
   (core/new-product txn bank-id data opts)))

(defn open-draft
  "Open a new draft version for an existing product. Returns the
  draft version map or an anomaly. Rejects if a draft already
  exists for this product.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id.
  - data: version fields (see `new-product`).
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn bank-id product-id data]
   (core/open-draft txn bank-id product-id data))
  ([txn bank-id product-id data opts]
   (core/open-draft txn bank-id product-id data opts)))

(defn update-draft
  "Replace a draft version's mutable fields in place. Returns the
  updated draft or an anomaly. Rejects if the version is not in
  draft state.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id.
  - version-id: version id.
  - data: version fields (see `new-product`).
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn bank-id product-id version-id data]
   (core/update-draft txn bank-id product-id version-id data))
  ([txn bank-id product-id version-id data opts]
   (core/update-draft txn bank-id product-id version-id data opts)))

(defn discard-draft
  "Discard an existing draft version. Returns the discarded
  version or an anomaly. Rejects if the version is not in draft
  state.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id.
  - version-id: version id.
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn bank-id product-id version-id]
   (core/discard-draft txn bank-id product-id version-id))
  ([txn bank-id product-id version-id opts]
   (core/discard-draft txn bank-id product-id version-id opts)))

(defn publish
  "Publish an existing draft version. Returns the published
  version or an anomaly. Rejects if the version is not in draft
  state.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id.
  - version-id: version id.
  - opts (optional): map; `:policies` overrides policy resolution."
  ([txn bank-id product-id version-id]
   (core/publish txn bank-id product-id version-id))
  ([txn bank-id product-id version-id opts]
   (core/publish txn bank-id product-id version-id opts)))

(defn new-template
  "Idempotently seed a platform cash-account-product template (no
  bank-id) — products are created from these. A stable `:template-id`
  makes re-seeding a no-op, preserving the original `:created-at`.
  Returns the template or an anomaly.

  Args:
  - config: FDB config (`:record-db` / `:record-store`).
  - data: template fields (`:product-type`, `:balance-sheet-side`,
    `:balance-products`, `:allowed-payment-address-schemes`,
    `:allowed-currencies`, `:iso-cash-account-type`, optional
    `:name` / `:internal`)."
  [config data]
  (core/new-template config data))
