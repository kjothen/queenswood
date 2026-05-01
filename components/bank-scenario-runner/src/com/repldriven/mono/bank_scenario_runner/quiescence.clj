(ns com.repldriven.mono.bank-scenario-runner.quiescence
  "Wait for the production system's read-side to catch up to the
  write-side before projecting state for comparison.

  Phase 3 verbs bypass Pulsar — `record-transaction` and
  `apply-legs` happen inside one `fdb/transact` so there's no
  CQRS lag to wait on. `wait` is a no-op stub here; when verbs
  start submitting through the command pipeline (Phase 5 onwards)
  this is where the changelog-cursor poll lives.

  See `docs/design/scenario-testing.md` (\"Async and quiescence\")
  for the design.")

(defn wait
  "Returns when the read-side has caught up with the write-side.
  Currently a no-op — verbs in `verbs.clj` are synchronous and
  commit-then-return. Replace with a changelog-cursor poll when
  any verb starts submitting via the Pulsar command pipeline.

  Returns `:quiescent` on success; an `:error/anomaly` on timeout
  is what callers should expect once the real implementation
  lands."
  [_bank]
  :quiescent)
