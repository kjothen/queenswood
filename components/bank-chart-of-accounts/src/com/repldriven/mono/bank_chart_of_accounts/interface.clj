(ns com.repldriven.mono.bank-chart-of-accounts.interface
  "Chart of accounts: the canonical set of bank-owned GL accounts a
  customer bank starts with (cash at correspondent, customer deposit
  controls, interest payable, suspense, etc.), plus the helpers that
  drive sub-ledger ↔ control reconciliation. A pure thin veneer over
  `bank-cash-account`: every CoA row is a `CashAccount` carrying GL
  discriminator fields (`:gl-code`, `:gl-account-type`,
  `:gl-account-class`, `:required`), and every customer cash account
  carries a `:gl-control-code` naming the GL control it rolls up
  into.

  This brick owns: the seven-row template, the
  product-type→control-code mapping, and the `mandatory?` predicate
  the CoA API uses to reject deletes of system-required accounts."
  (:require
    [com.repldriven.mono.bank-chart-of-accounts.core :as core]
    [com.repldriven.mono.bank-chart-of-accounts.domain :as domain]))

(def
  ^{:doc
    "The canonical chart of accounts every customer bank is seeded
  with at provisioning time. Vector of maps with `:gl-code`,
  `:name`, `:gl-account-type`, `:gl-account-class`, `:required`."}
  template
  domain/template)

(def
  ^{:doc
    "Map from cash-account product-type keyword to the GL control
  account's `:gl-code` that its balance rolls up into. Customer cash
  accounts opened with one of these product types carry the matching
  `:gl-control-code`, and every posting on them fans out to a
  matching leg on the control."}
  control-code-for-product-type
  domain/control-code-for-product-type)

(defn mandatory?
  "True if `gl-code` names a mandatory seeded account that cannot be
  deleted via the chart-of-accounts API. False for optional rows and
  for unknown codes.

  Args:
  - gl-code: GL account code string (e.g. \"1100\")."
  [gl-code]
  (domain/mandatory? gl-code))

(defn seed!
  "Provision the canonical chart of accounts for `bank-id`. Creates
  one seed product per currency and the seven template accounts on
  each, all owned by `party-id` (the bank's own organization-party).
  Returns a flat vector of created accounts, or an anomaly on the
  first failure (caller is expected to wrap in `store/transact` so
  partial seeds roll back).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - party-id: the bank's own organization-party id.
  - currencies: vector of ISO 4217 currency strings.
  - policies: effective policies for capability and limit checks."
  [txn bank-id party-id currencies policies]
  (core/seed! txn bank-id party-id currencies policies))

(defn expand-legs
  "Walk `legs` and append a matching control-side leg for every
  customer sub-ledger leg whose account carries a `:gl-control-code`.
  Posting sites (`bank-payment`, `bank-interest`) call this BEFORE
  `transactions/record-transaction` so the synthetic control leg
  lands atomically in the same FDB transaction as the originals.
  GL-only legs pass through unchanged. Returns the expanded leg
  vector, or an anomaly on lookup failure.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - legs: original transaction legs."
  [txn bank-id legs]
  (core/expand-legs txn bank-id legs))
