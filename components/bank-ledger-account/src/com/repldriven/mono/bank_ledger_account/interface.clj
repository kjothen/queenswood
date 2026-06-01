(ns com.repldriven.mono.bank-ledger-account.interface
  "Bank-owned general-ledger accounts — the chart of accounts a
  customer bank runs its own books on (cash at correspondent,
  customer-deposit controls, interest payable, suspense, etc.). A
  `LedgerAccount` is a flat, bank-owned record distinct from a
  customer `CashAccount`: 1:1 with a chart row, created directly by
  `seed!`, with no product, no versioning, and no command/watcher
  lifecycle. Ledger accounts share the `account-id` space with cash
  accounts, so a `:ledger-account-id` is just another `account-id` to
  `bank-balance` and `bank-transaction` — which is what keeps a
  customer leg and its control-account leg atomic in one posting.

  This brick owns: the canonical seven-row template, the
  product-type to control-code mapping, the `mandatory?` predicate,
  the `seed!` provisioning call, code/id lookups, and the
  `expand-legs` paired-leg construction posting sites use to fan a
  customer leg out to its control account."
  (:require
    [com.repldriven.mono.bank-ledger-account.core :as core]
    [com.repldriven.mono.bank-ledger-account.domain :as domain]))

(def
  ^{:doc
    "The canonical chart of accounts a customer bank starts with,
  as a vector of template rows (`:gl-code`, `:name`,
  `:gl-account-type`, `:gl-account-class`, `:required`)."}
  template
  domain/template)

(def
  ^{:doc
    "Map from cash-account product-type keyword to the control
  ledger account's `:gl-code` its balance rolls up into. Postings on
  customer accounts of these product types fan out to a matching leg
  on the control."}
  control-code-for-product-type
  domain/control-code-for-product-type)

(defn mandatory?
  "True if `gl-code` names a mandatory seeded account; false for
  optional and unknown codes. The ledger-account API uses this to
  reject deletes of system-required accounts.

  Args:
  - gl-code: GL account code string (e.g. \"2100\")."
  [gl-code]
  (domain/mandatory? gl-code))

(defn seed!
  "Provision the canonical chart of accounts for `bank-id`. For each
  currency, creates one `LedgerAccount` per template row (seven
  total) and its single default-posted opening balance. Returns a
  flat vector of every created account, or an anomaly on the first
  failure (the surrounding transaction rolls the whole thing back).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - currencies: vector of ISO 4217 currency strings."
  [txn bank-id currencies]
  (core/seed! txn bank-id currencies))

(defn get-account
  "Return the `LedgerAccount` matching `(bank-id, ledger-account-id)`,
  or nil. Used by the ledger-account API's existence guard.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - ledger-account-id: ledger account id (`led.<ulid>`)."
  [txn bank-id ledger-account-id]
  (core/get-account txn bank-id ledger-account-id))

(defn find-by-code
  "Resolve a ledger account from its `gl-code`. Returns the
  `LedgerAccount` map (or nil if not seeded). Used by posting sites
  to find counter-leg accounts like 1100 (cash at correspondent),
  2400 (interest payable), 2500 (suspense).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - gl-code: GL account code string (e.g. \"1100\")."
  [txn bank-id gl-code]
  (core/find-by-code txn bank-id gl-code))

(defn list-accounts
  "Return every `LedgerAccount` for `bank-id` (the bank's full chart),
  as a vector.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id."
  [txn bank-id]
  (core/list-accounts txn bank-id))

(defn expand-legs
  "Walk `legs` and append a matching control-side leg for every
  customer default-posted leg carrying a sub-ledger `:product-type`,
  resolving the control ledger account from that product type.
  Posting sites call this BEFORE recording the transaction so the
  synthetic control leg lands atomically in the same transaction.
  Legs without a customer product type pass through unchanged.
  Returns the expanded leg vector, or an anomaly on lookup failure.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - legs: original transaction legs (customer legs carry
    `:product-type`)."
  [txn bank-id legs]
  (core/expand-legs txn bank-id legs))
