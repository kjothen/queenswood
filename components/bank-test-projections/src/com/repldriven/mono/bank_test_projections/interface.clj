(ns com.repldriven.mono.bank-test-projections.interface
  "Read-side projections for scenario testing. Each `project-*` fn
  reduces real-system state — read through production component
  interfaces only — to the same shape as some part of the model
  state in `bank-test-model`. The runner pairs a real projection
  with its model counterpart and compares for equality."
  (:require
    [com.repldriven.mono.bank-test-projections.accounts :as accounts]
    [com.repldriven.mono.bank-test-projections.balances :as balances]
    [com.repldriven.mono.bank-test-projections.orgs :as orgs]
    [com.repldriven.mono.bank-test-projections.parties :as parties]
    [com.repldriven.mono.bank-test-projections.payments :as payments]
    [com.repldriven.mono.bank-test-projections.products :as products]
    [com.repldriven.mono.bank-test-projections.transactions :as transactions]))

(def
  ^{:doc
    "Real-side available-balance projection. Reads each
  account in `id-mapping` through `bank-balance/get-balances` and
  returns `{model-acct-id -> int-pence}`. Args:
  - bank: FDB config map.
  - id-mapping: `{real-id -> model-id}` map of in-scope accounts."}
  project-balances
  balances/project-balances)

(def
  ^{:doc
    "Model-side available-balance projection. Returns
  `{model-acct-id -> int-pence}` for direct equality with
  `project-balances`. Args:
  - model-state: model state map."}
  project-model-balances
  balances/project-model-balances)

(def
  ^{:doc
    "Real-side product version-history projection. Reads
  each org's products via `get-products` and emits
  `{model-prod-id [{:status ... :number n} ...]}` ordered
  descending by `:number`. Args:
  - bank: FDB config map.
  - model->real: `{model-prod-id {:real-id <id>
                                  :org-real-id <org>}}`."}
  project-products
  products/project-products)

(def
  ^{:doc
    "Model-side product version-history projection. Same
  shape as `project-products`, with versions reversed to descending
  order. Args:
  - model-state: model state map."}
  project-model-products
  products/project-model-products)

(def
  ^{:doc
    "Real-side party-status projection. Returns
  `{model-party-id :active|:pending}`. Args:
  - bank: FDB config map.
  - model->real: `{model-party-id {:real-id <id>
                                    :org-real-id <org>}}`."}
  project-parties
  parties/project-parties)

(def
  ^{:doc
    "Model-side party-status projection. Returns
  `{model-party-id :active|:pending}`. Args:
  - model-state: model state map."}
  project-model-parties
  parties/project-model-parties)

(def
  ^{:doc
    "Real-side org-membership projection — for each tracked
  org, the set of accounts, products and parties that belong to it.
  Returns `{model-org-id {:accounts #{...} :products #{...}
                          :parties #{...}}}`. Args:
  - bank: FDB config map.
  - ctx: runner context with `:orgs`, `:id-mapping`, `:products`,
    `:parties`."}
  project-orgs
  orgs/project-orgs)

(def
  ^{:doc
    "Model-side org-membership projection. Same shape as
  `project-orgs`, normalised to sets. Args:
  - model-state: model state map."}
  project-model-orgs
  orgs/project-model-orgs)

(def
  ^{:doc
    "Real-side per-account projection: `:org`, `:product`,
  `:party`, normalised `:status`. Returns
  `{model-acct-id {:org :model-org :product :model-prod
                   :party :model-party :status :open|:closed}}`.
  Args:
  - bank: FDB config map.
  - ctx: runner context with `:id-mapping`, `:accounts`, `:orgs`,
    `:products`, `:parties`."}
  project-accounts
  accounts/project-accounts)

(def
  ^{:doc
    "Model-side per-account projection. Same shape as
  `project-accounts`. Args:
  - model-state: model state map."}
  project-model-accounts
  accounts/project-model-accounts)

(def
  ^{:doc
    "Real-side transaction-leg-count projection — counts
  legs touching each account via `bank-transaction/get-transactions`.
  Returns `{model-acct-id leg-count}`. Args:
  - bank: FDB config map.
  - id-mapping: `{real-id -> model-id}` map."}
  project-transactions
  transactions/project-transactions)

(def
  ^{:doc
    "Model-side transaction-leg-count projection. Returns
  `{model-acct-id leg-count}`. Args:
  - model-state: model state map."}
  project-model-transactions
  transactions/project-model-transactions)

(def
  ^{:doc
    "Real-side outbound-payment projection. Returns
  `{model-payment-id :pending|:completed|nil}` — nil signals the
  record is missing. Args:
  - bank: FDB config map.
  - model->real: `{model-payment-id {:real-id <id>}}`."}
  project-outbound-payments
  payments/project-outbound-payments)

(def
  ^{:doc
    "Model-side outbound-payment projection. Returns
  `{model-payment-id :pending|:completed}`. Args:
  - model-state: model state map."}
  project-model-outbound-payments
  payments/project-model-outbound-payments)

(def
  ^{:doc
    "Real-side inbound-payment projection. Returns the set
  of stx-markers actually present in the bank, looked up by
  deterministic stx-id. Args:
  - bank: FDB config map.
  - run-id: runner `:run-id` used to construct stx-ids.
  - markers: collection of stx-marker keywords to probe."}
  project-inbound-payments
  payments/project-inbound-payments)

(def
  ^{:doc
    "Model-side inbound-payment projection. Returns the
  set of stx-markers the model has settled. Args:
  - model-state: model state map."}
  project-model-inbound-payments
  payments/project-model-inbound-payments)
