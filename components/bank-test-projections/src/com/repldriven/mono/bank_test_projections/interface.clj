(ns com.repldriven.mono.bank-test-projections.interface
  "Read-side projections for scenario testing. Each `project-*` fn
  reduces real-system state to the same shape as some part of the
  model state in `bank-test-model`. The runner pairs a real
  projection with its model counterpart and compares for equality.

  Projections depend on production component **interfaces only** —
  never internals — and call through the same query path the API
  uses. See `docs/tdd/scenario-testing.md`."
  (:require
    [com.repldriven.mono.bank-test-projections.accounts :as accounts]
    [com.repldriven.mono.bank-test-projections.balances :as balances]
    [com.repldriven.mono.bank-test-projections.orgs :as orgs]
    [com.repldriven.mono.bank-test-projections.parties :as parties]
    [com.repldriven.mono.bank-test-projections.payments :as payments]
    [com.repldriven.mono.bank-test-projections.products :as products]
    [com.repldriven.mono.bank-test-projections.transactions :as transactions]))

(def project-balances balances/project-balances)
(def project-model-balances balances/project-model-balances)

(def project-products products/project-products)
(def project-model-products products/project-model-products)

(def project-parties parties/project-parties)
(def project-model-parties parties/project-model-parties)

(def project-orgs orgs/project-orgs)
(def project-model-orgs orgs/project-model-orgs)

(def project-accounts accounts/project-accounts)
(def project-model-accounts accounts/project-model-accounts)

(def project-transactions transactions/project-transactions)
(def project-model-transactions transactions/project-model-transactions)

(def project-outbound-payments payments/project-outbound-payments)
(def project-model-outbound-payments payments/project-model-outbound-payments)

(def project-inbound-payments payments/project-inbound-payments)
(def project-model-inbound-payments payments/project-model-inbound-payments)
