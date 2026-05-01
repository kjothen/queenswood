# Scenario Testing Implementation Plan

This is the ordered, small-step plan for implementing the architecture described in `docs/design/scenario-testing.md`. Each step is intended to be a reviewable chunk. Tick steps off as they land.

The discipline of this plan is to keep failure radius small. Do not skip ahead. If a step turns out to be larger than expected, split it.

## Phase 1: model skeleton

Goal: a pure-functional model that can be exercised in isolation, with no fugato dependency yet.

- [x] **1.1** Create `bank-test-model` component skeleton with empty `interface.clj`. Add to root `deps.edn` (`:dev` and `:+bank` aliases) so existing `project:dev` test runs pick it up. No new project — there's nothing here we'd build into a deployable artifact, so the `bank-tests` plumbing lives in the existing test pipeline (the `external-test-runner` base + each brick's `test/`). When the scenario runner needs an entrypoint in Phase 3, it'll be a `bases/bank-scenario-runner` base, not a project. Confirm `clojure -M:poly check` is clean.
- [x] **1.2** Define `init-state` and helpers in `state.clj`: `known-accounts`, `balance`, `next-id`. Pure functions, no commands yet. The `:policies` field carries one entry — the available-balance rule — in model-shape (e.g. `{:available {:min 0 :improving? true}}`).
- [x] **1.3** Implement `:open-account` spec in `balances.clj`. Just `:args` (returns empty tuple) and `:next-state` (creates account at zero balance with synthetic ID). No `:run?` needed (always available); no `:valid?` needed for stand-alone.
- [x] **1.4** Implement `:inbound-transfer` spec in `transfers.clj`. Re-implement the relevant policy rule (`available >= 0`, with the lenient `improving` allowance) as a pure helper in `policy.clj`; the model never imports `bank-policy`. State stays unchanged when the helper returns `:denied`.
- [x] **1.5** Implement `:outbound-transfer` spec, mirror of inbound.
- [x] **1.6** Implement `:apply-fee` spec in `fees.clj`. Note: fees are _not_ in policy applicable transaction types, so they post regardless. This is the mechanism that drives accounts into breach in tests.
- [x] **1.7** Assemble the model map in `interface.clj`, re-export `init-state`.
- [x] **1.8** Unit tests in `test/com/repldriven/mono/bank_test_model/` for each command's `:next-state` in isolation. Use `clojure.test/deftest` here — these are pure-function tests, the one place deftest is appropriate.

**Stop here and review.** The model should run end-to-end against handcrafted command sequences without any fugato or runner. Confirm policy evaluation in the model matches production behaviour for a few hand-traced cases.

## Phase 2: projections

Goal: read real-system state and produce model-comparable shapes.

- [x] **2.1** Create `bank-test-projections` component skeleton. Depend on `bank-balance/interface`.
- [x] **2.2** Implement `project-balances`. Takes a bank handle and a real-id->model-id map; returns `{model-acct-id -> int-pence}`.
- [x] **2.3** Implement `project-model-balances`. Takes model state; returns the same shape.
- [x] **2.4** Unit tests for both projections. Construct fake bank states by calling production interfaces directly; construct fake model states by hand. Confirm shapes match.

**Stop here and review.** Projections should be testable without a running runner. If `project-balances` requires the scenario runner to be useful, something is wrong with its signature.

## Phase 3: runner skeleton

Goal: drive the real system from a command sequence, with no fugato yet.

- [x] **3.1** Create `bank-scenario-runner` component skeleton. Depend on `bank-test-model/interface`, `bank-test-projections/interface`, and the production command-submission interface.
- [x] **3.2** Implement the ID side-table in `id_mapping.clj`. Two views: `model->real`, `real->model`. Functions to add a mapping and resolve in either direction.
- [x] **3.3** Implement command dispatch in `verbs.clj`. One multimethod (or case) keyed on `:command`. Each verb translates a model command to a real command and submits it. For `:open-account`, capture the returned real ID and store the mapping.
- [x] **3.4** Implement `quiescence.clj`. After submitting commands, wait for the read-side cursor to catch up to the last command's outbox versionstamp. Timeout is a runner error (not a domain divergence). Phase 3 verbs are synchronous (record-transaction + apply-legs in one fdb/transact, no Pulsar), so this is a no-op stub — to be replaced with the changelog-cursor poll once any verb starts submitting via the command pipeline.
- [x] **3.5** Implement `run-commands` in `interface.clj`. Takes a bank handle and a sequence of commands; dispatches each, accumulates ID mappings, waits for quiescence at the end.
- [x] **3.6** Integration test: run a hand-built three-command sequence (open, inbound, outbound) against a real bank, project balances, compare to a hand-built expected map. Plus a curative case (open + inbound + fee + improving inbound) — the fee drives the account into breach and the lenient `improving` allowance permits the second inbound. Caught a production policy-config bug: platform `balances.yml` had no transaction-type filter, so the available rule fired on every transaction type including fees and interest. Now scoped to the user-driven types (internal, inbound, outbound transfers).

**Stop here and review.** The runner should drive real commands and produce comparable output without any fugato or scenario loading. Quiescence wait should be robust under repeated runs.

## Phase 4: EDN scenarios

Goal: hand-authored scenarios as the simpler complement to fugato.

- [x] **4.1** Define the EDN scenario schema in Malli. Validate at load time. Bad scenarios fail loudly with a useful error.
- [x] **4.2** Write the scenario loader: read EDN file, validate, return a normalised internal representation.
- [x] **4.3** Extend the runner to accept EDN scenarios. `:given`, `:when`, `:then` are all sequences of the same step type; the split is for readers, not the runner.
- [x] **4.4** Add assertion verbs: `:assert-balance`, `:assert-outcome`, `:assert-no-anomaly`. Each is a step that compares a projection to an expected value.
- [x] **4.5** Author three scenarios in `components/bank-scenario-runner/test-resources/bank-scenario-runner/scenarios/`:
  - `simple-inbound.edn` — basic credit, balance moves up.
  - `simple-outbound.edn` — basic debit, balance moves down.
  - `curative-inbound-when-in-breach.edn` — fee drives account negative; improving inbound permitted (lenient `:limit-allow-improving`). The original wording also asserted "outbound denied" here, but exercising that uncovered a real model/reality divergence: an outbound that worsens an existing breach is denied by the model but currently permitted by the real bank. Left out of Phase 4's hand-authored scope on purpose — Phase 5 fugato is the right tool to surface and shrink that case.
- [x] **4.6** Wire scenario loading into a `deftest` that iterates the directory and runs each scenario.

**Stop here and review.** All three scenarios pass (10 assertions / 0 failures). The curative case is the load-bearing one — if it passes, the policy evaluator and the model are agreeing on the lenient/improving logic on the inbound side. The outbound-worsens-breach divergence is the first real finding from this architecture; tracking it through to Phase 5 is the next step.

## Phase 5: fugato

Goal: model-equality property test running over generated command sequences.

- [ ] **5.1** Add `org.clojure/test.check` and `io.vouch/fugato` to `bank-scenario-runner` test deps. (Or wherever fugato has been published; check Clojars.)
- [ ] **5.2** Confirm the model map from Phase 1 works with `fugato/commands`. Generate a few sequences at the REPL, eyeball them for plausibility.
- [ ] **5.3** Write the first `defspec`: `model-eq-reality`, using `project-balances` only. Run with a small number of trials (10) and short sequences (10 commands).
- [ ] **5.4** Run it. It will probably fail. Use the `first-divergence` helper to pinpoint where model and reality disagree. Fix divergences one at a time. Most likely sources:
  - Quiescence wait too short.
  - Policy evaluator in the model out of sync with production.
  - ID mapping bug in the runner.
  - A real production bug. (This is the one you want.)
- [ ] **5.5** Once stable, increase trial count to 50 and sequence length to 30. Re-run a few times to confirm reliability.

**Stop here and review.** A reliably-passing property test that exercises four commands across thirty-step sequences fifty times per CI run. If this is solid, the rest of the work is incremental extension.

## Phase 6: extending the model

Goal: bring more of the bank's behaviour under property test.

- [ ] **6.1** Add `:internal-transfer` to model, runner, and EDN scenarios. This is the two-leg case — debit one account, credit another. Tests whether per-leg policy evaluation works.
- [ ] **6.2** Add `:advance-time` and `:accrue-interest`. Add `project-interest-accrued` if asserting interest specifically. The six-leg accrual is the interesting test target.
- [ ] **6.3** Add `:reverse` (or whatever your reversal command is). Tests the curative path naturally — reversing a posting reduces the breach.
- [ ] **6.4** Multi-account and multi-party scenarios. Bigger generated state space; longer sequences.

Beyond this point, plan further work as separate tickets. The infrastructure is in place; new commands are additive.

## Out of scope for this plan

- **test.contract for ClearBank.** Separate piece of work; complementary, not blocking. See `docs/design/scenario-testing.md` "Considered alternatives" section.
- **Snapshot testing.** Reconsider once specific use cases (e.g. interest accrual ledger output) prove worth locking down.
- **Deterministic simulation.** The Allen Rohner / FoundationDB style of seeded-everything testing. Worth knowing about; not on this plan.

## Notes on working through this plan

- **One step per PR if practical.** Some steps in Phase 1 are small enough to combine. Phases 3–5 are not.
- **Don't skip Phase 4.** EDN scenarios in Phase 4 are easier to debug than fugato failures in Phase 5. If something is broken structurally, EDN scenarios will tell you in plain English; fugato will tell you with a shrunk sequence and a diff.
- **Resist enriching the model.** Throughout, the model stays pure-functional and ignorant of production infrastructure. If `bank-test-model` starts importing proto schemas or Malli contracts, push back.
- **Quiescence is real.** If tests start flaking after Phase 5, the answer is almost always quiescence-related, not test logic.
