(ns com.repldriven.mono.bank-chart-of-accounts.interface
  "Chart of accounts: the canonical set of bank-owned GL accounts a
  customer bank starts with (cash at correspondent, customer deposit
  controls, interest payable, suspense, etc.), plus the helpers that
  drive sub-ledger ↔ control reconciliation. A thin veneer over
  `bank-cash-account-product` and `bank-cash-account`: each GL row is
  a `CashAccountProduct` of `kind :general-ledger` carrying GL fields
  (gl-code, gl-account-type, gl-account-class, required) plus the
  single `CashAccount` opened under it. Customer cash accounts carry a
  `:gl-control-account-id` pointing directly at the matching control
  GL account.

  This brick owns: the seven-row template, the
  product-type→control-code mapping, the `mandatory?` predicate the
  CoA API uses to reject deletes of system-required accounts, the
  `find-gl-account-by-code` lookup, and the `expand-legs` paired-leg
  construction."
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
  `:gl-control-account-id`, and every posting on them fans out to a
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
  "Provision the canonical chart of accounts for `bank-id`. For each
  currency, creates one GL product per template row (seven total) and
  the single CashAccount under each. All accounts owned by `party-id`
  (the bank's own organization-party). Returns a flat vector of every
  created account, or an anomaly on the first failure (caller is
  expected to wrap in `store/transact` so partial seeds roll back).

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
  customer sub-ledger leg whose account carries a
  `:gl-control-account-id`. Posting sites (`bank-payment`,
  `bank-interest`) call this BEFORE
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

(defn find-gl-account-by-code
  "Resolve a GL account from its code. Returns the CashAccount map
  (or nil if the GL product or its account isn't seeded yet). Used by
  posting sites to find counter-leg accounts like 1100 (cash at
  correspondent), 2400 (interest payable), 4100 (fee income).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - gl-code: GL account code string (e.g. \"1100\")."
  [txn bank-id gl-code]
  (core/find-gl-account-by-code txn bank-id gl-code))
