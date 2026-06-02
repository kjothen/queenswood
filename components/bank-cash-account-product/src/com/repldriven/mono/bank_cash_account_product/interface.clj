(ns com.repldriven.mono.bank-cash-account-product.interface
  "Cash-account product catalog: per-bank product definitions
  that drive cash-account opening. Products carry a `:kind`
  discriminator with two variants — `:sub-ledger` (customer-facing
  instruments — current, savings, term-deposit) and
  `:general-ledger` (bank-owned chart-of-accounts entries). Sub-ledger
  products drive instrument opening (allowed currencies, balance
  bucket layout, payment-address schemes, interest rate); GL products
  drive chart-of-accounts seeding (gl-code, gl-account-type,
  gl-account-class). Both kinds versioned through draft, published,
  and discarded states; only published versions can back live
  accounts."
  (:require
    [com.repldriven.mono.bank-cash-account-product.core :as core]
    [com.repldriven.mono.bank-cash-account-product.resources :as resources]
    [com.repldriven.mono.bank-cash-account-product.store :as store]))

(defn new-product
  "Create a new product as an initial draft v1. Returns the
  draft version map or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - data: version fields:
    - `:name` — product display name (required).
    - `:currency` — ISO 4217 string, single currency per product.
    - `:valid-from` — optional ISO timestamp.
    - `:kind` — discriminator map naming the variant. For a
      customer-facing product:
      `{:sub-ledger {:product-type :product-type-sub-ledger-current
                     :interest-rate-bps 0
                     :iso-cash-account-type
                     :iso-cash-account-type-cacc}}`
      For a GL product:
      `{:general-ledger {:gl-code \"2100\"
                         :gl-account-type :gl-account-type-liability
                         :gl-account-class :gl-account-class-control
                         :required :required-mandatory
                         :sub-ledger-kind
                         :sub-ledger-kind-cash-account-current}}`
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

(defn find-product-by-gl-code
  "Return the first CashAccountProduct whose general_ledger variant
  has the given `gl-code` for this bank, or nil. Used by
  `bank-chart-of-accounts` and by cash-account opening to resolve a GL
  account from its code. Composes with
  `bank-cash-account/find-account-by-product` to fetch the actual
  CashAccount.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - gl-code: GL account code string (e.g. \"2100\")."
  [txn bank-id gl-code]
  (store/find-product-by-gl-code txn bank-id gl-code))

(def ^:private internal-product-types
  "Product types the bank provisions for its own internal accounts
  (not customer-facing instruments), excluded from the product-template
  menu an operator chooses from."
  #{:product-type-sub-ledger-own-funds})

(defn list-templates
  "Return the customer-facing per-product-type templates as a vector of
  maps with a `:product-type` key plus the derived fields applied at
  product creation. Static today, loaded from classpath at brick init;
  intended to move to per-organization FDB records later so an
  operator can author their own product templates."
  []
  (->> resources/product-defaults
       (remove (fn [[t _]] (contains? internal-product-types t)))
       (map (fn [[t fields]] (assoc fields :product-type t)))
       (sort-by :product-type)
       vec))

(defn published-version
  "Return the highest-version-number `:published` version in a
  product aggregate (as returned by `get-product`), or nil if
  none. Relies on the aggregate's `:versions` being sorted
  newest-first.

  Args:
  - product: a product aggregate map with a `:versions` vector."
  [{:keys [versions]}]
  (->> versions
       (filter (fn [v]
                 (= :cash-account-product-status-published (:status v))))
       first))
