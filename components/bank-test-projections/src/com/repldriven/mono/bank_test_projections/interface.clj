(ns com.repldriven.mono.bank-test-projections.interface
  "Read-side projections for scenario testing. Each `project-*` fn
  reduces real-system state to the same shape as some part of the
  model state in `bank-test-model`. The runner pairs a real
  projection with its model counterpart and compares for equality.

  Projections depend on production component **interfaces only** —
  never internals — and call through the same query path the API
  uses. See `docs/design/scenario-testing.md`."
  (:require
    [com.repldriven.mono.bank-test-projections.balances :as balances]))

(def project-balances balances/project-balances)

(def project-model-balances balances/project-model-balances)
