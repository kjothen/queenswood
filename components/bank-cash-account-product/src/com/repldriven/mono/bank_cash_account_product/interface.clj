(ns com.repldriven.mono.bank-cash-account-product.interface
  "Cash-account product catalog: per-bank customer-product definitions
  that drive cash-account opening — allowed currencies, balance bucket
  layout, payment-address schemes, interest rate, and the ISO 20022
  account-type classification. A product is versioned through draft,
  published, and discarded states; the published version effective
  today (see `active-version`) backs newly-opened accounts."
  (:require
    com.repldriven.mono.bank-cash-account-product.system

    [com.repldriven.mono.bank-cash-account-product.core :as core]
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]))

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

(defn get-version
  "Load a single version. Returns the version map or a rejection
  anomaly if not found.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id.
  - version-id: version id."
  [txn bank-id product-id version-id]
  (core/get-version txn bank-id product-id version-id))

(defn get-product
  "Return `{:product-id <pid> :versions [...]}` for one product,
  newest version first.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - product-id: product id."
  [txn bank-id product-id]
  (core/get-product txn bank-id product-id))

(defn get-products
  "Return `{:items [{:product-id ... :versions [...]} ...]}` for
  an organization, grouped by product-id in scan order.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - opts (optional): map; `:limit`, `:order`."
  ([txn bank-id] (core/get-products txn bank-id))
  ([txn bank-id opts] (core/get-products txn bank-id opts)))

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

(defn get-template
  "Load a template by id. Returns the template map or a
  `:cash-account-product/template-not-found` rejection.

  Args:
  - txn: FDB transaction or db handle.
  - template-id: template id."
  [txn template-id]
  (core/get-template txn template-id))

(defn list-templates
  "Return the customer-facing platform templates (internal ones such as
  own-funds excluded), sorted by product-type.

  Args:
  - txn: FDB transaction or db handle."
  [txn]
  (core/list-templates txn))

(defn active-version
  "Return the published version of a product aggregate (as returned by
  `get-product`) effective on epoch-day `as-of`, or nil if none — the
  published version whose `[effective-from, effective-to)` window
  contains `as-of`, choosing the greatest effective-from.

  Args:
  - product: a product aggregate map with a `:versions` vector.
  - as-of: epoch-day (long) to evaluate the window at (e.g.
    `utility/today`)."
  [product as-of]
  (domain/active-version product as-of))
