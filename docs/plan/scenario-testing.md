# Scenario Testing Implementation Plan

This is the ordered, small-step plan for implementing the architecture described in `docs/tdd/scenario-testing.md`. Each step is intended to be a reviewable chunk. Tick steps off as they land.

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

- [x] **5.1** Add `org.clojure/test.check` and `io.vouch/fugato` to `bank-scenario-runner` test deps. Fugato is unreleased on Clojars; pulled as a git dep pinned to `vouch-opensource/fugato` SHA `4c1f687b`.
- [x] **5.2** Confirm the model map from Phase 1 works with `fugato/commands`. Sanity `deftest` generates 5 sequences of ≥3 steps and asserts each step has a known `:command` and a vector `:args`.
- [x] **5.3** Write the first `defspec`: `model-eq-reality`, using `project-balances` only. Started at 10 trials × max-size 10. Each trial spins up its own FDB testcontainer.
- [x] **5.4** Skipped — the headline divergence (worsening outbound permitted because the platform `BalanceLimit` was silently dead through proto-record/plain-map equality) was already root-caused and fixed before Phase 5 started. No new divergences appeared.
- [x] **5.5** Scaled to 50 trials × max-size 30. Three consecutive runs each ~3m20s, all green. Reliable.
- [x] **5.6** Hoisted `with-test-system` out of `prop/for-all` so one FDB testcontainer serves all trials. Per-trial isolation now comes from a fresh runner context: own id-mapping, fresh `:run-id` (uuidv7) prefixed onto idempotency keys so multiple trials against the same bank don't collide on the dedup index. Switched from `defspec` to `deftest` + `tc/quick-check` to control system lifecycle. Bumped the test policy's customer-org limit (100 → 100,000) so trial 34 onwards doesn't trip an org-count cap that's outside what the property is testing. **Result: 50×30 in ~13s, down from ~3m20s — ≈15× speedup.**

**Stop here and review.** Four commands (`:open-account`, `:inbound-transfer`, `:outbound-transfer`, `:apply-fee`) across sequences up to 30 steps, 50 trials per run, no divergence, ~13s per run. The architecture's load-bearing assumption — that re-implementing policy logic in a tiny pure model is enough to surface real-system bugs — held: it found a silent production bug before the property test could even run, and the optimization step caught a second one (org-count limit hit by accumulated test state). Phase 6 extends the model with more verbs.

## Phase 6: extending the model

Goal: bring more of the bank's behaviour under property test.

- [x] **6.1** Add `:internal-transfer` to model, runner, and EDN scenarios. Two-leg, atomic — both legs pass the available rule or neither applies. Model `:run?` requires ≥2 accounts; `:args` picks two distinct ids via `gen/such-that`. Runner verb constructs both legs as `:balance-type-default` with `:transaction-type-internal-transfer`. EDN scenario `internal-transfer.edn` exercises the happy path; the property test now generates `:internal-transfer` as one of five commands and stayed green across three consecutive runs.
- [x] **6.2a** Multi-account-per-org structural primitive. Model state gains `:orgs`, `:next-org-id`, plus an `:org` field on each account. `:open-account` now allocates an org alongside its first account; new `:add-account [:org]` opens additional accounts under an existing org. Runner stashes `:real-id`/`:party-id`/`:currency` in `ctx :orgs <model-org>` so the verb can call `cash-accounts/new-account` re-using the auto-created party. EDN scenario `intra-org-internal-transfer.edn` exercises the case.
- [x] **6.2b** Products with create/publish lifecycle. Model state gains `:products`, `:next-product-id`. `:open-account` auto-allocates a published settlement product alongside the org; new `:create-product [:org]` (draft) and `:publish-product [:prod]` commands. `:add-account` becomes `[:org :prod]` requiring the product to be published and to belong to the org. Runner mirrors via `bank-cash-account-product/new-product` (passing `:balance-products` and `:allowed-payment-address-schemes` defaults — a missing dep the property test surfaced) and `.../publish`. EDN scenario `custom-product-account.edn` exercises the create→publish→add-account chain. **Side finding:** while debugging, traced the `range` bound's `bound-violation` in `bank-policy/limit.clj` — `(violates :max (:max payload))` passes a sub-bound where an aggregate is expected, so range limits silently never fire. The test currently relies on this papering over; revisit when fixing the policy evaluator.
- [x] **6.2c-1** Parties as first-class (structural-only). Model state gains `:parties` keyed by party-id, plus a `:parties` list on each org. `:open-account` auto-allocates an active organization-party (`:party-0`) alongside the org. `:add-account` signature is now `[:org :party :prod]`, with `:run?`/`:valid?` requiring the party to belong to the org and be `:active`. Runner ctx tracks model→real party mapping at `:parties`. Existing scenarios updated. The auto-org-party is the only party available so far — no new generation surface for fugato until 6.2c-2 lands person-parties.
- [x] **6.2c-2** Person-parties + activation. Added `bank-party.interface/seed-active-party` (test/admin shortcut: load → `domain/activate-party` → `store/save-party`, bypassing the IDV → changelog-watcher path). Documented as transitional until an IDV simulator base lands. Model gains `:create-person-party [:org]` (pending) and `:activate-party [:party]` (pending → active). Property test surfaced one mismatch immediately: the runner's `:create-person-party` was missing `date-of-birth` and `nationality` (proto-required on `PersonIdentification`), so creates silently failed → fugato shrank to a 8-step reproducer in seconds. With those fields added the property test stayed green across runs (3 × 50 trials, 10 commands now generated). EDN scenario `person-party-account.edn` exercises the create → activate → add-account chain.
- [x] **6.2d** Outbound payment via the scheme path. Added `submit-outbound` to `bank-payment.interface`. Runner verb `:outbound-payment [:acct amount]` routes through `submit-outbound` (debits customer, credits settlement-suspense, persists `OutboundPayment` as pending, publishes a scheme command). Model spec mirrors `:outbound-transfer` since balance behaviour is identical; the property test exercises both paths. Property test surfaced a missing `:scheme`/`:creditor-name` (proto-required) in the runner's submit data on the first run — fugato shrank to a 3-step reproducer in <1s. Quiescence stays a stub: legs apply synchronously inside `submit-outbound`; only the scheme adapter side is async, and balances aren't affected by the adapter's reply.
- [x] **6.3** Interest, EDN-only. Exposed `accrue-daily` and `capitalize-monthly` on `bank-interest.interface`. Runner verbs `:accrue-interest [:org date]` and `:capitalize-interest [:org date]`, plus `:create-savings-product [:org rate-bps]` for setting up a non-zero-rate product. None added to the model — interest math is too easy to drift; reserve for hand-authored EDN scenarios. Scenario `interest-accrual.edn` runs the accrual + capitalisation chain on a £1,000 savings balance at 36.5% AER and asserts no anomalies. Locking in the actual interest amounts needs richer projections (interest-paid / interest-accrued balance-types aren't part of `available-balance`) — see 6.x.
- [x] **6.x-projections** Built out `bank-test-projections`. Was just `project-balances`; now also exposes `project-products` (`:draft|:published` per model-prod) and `project-parties` (`:active|:pending` per model-party), with their model-side counterparts. Property test's `run-and-compare` now compares `{:balances :products :parties}` per trial — much more comparison surface, catches divergences (failed creates, mistyped statuses) earlier than they'd surface via balances alone.
- [x] **7.1** EDN scenarios fold step-by-step through the model alongside the runner. After every modelled step, projects both ends and asserts equality. Cuts off on the first non-model command in a scenario (assertions are neutral). Every modelable scenario now has model-equality coverage; previously they only had hand-computed `:assert-balance` checks.
- [x] **7.2** Interest math in the model. Ported `daily-interest` (carry-aware, integer micro-unit arithmetic) from `bank-interest.domain` to `bank-test-model.interest`; `:accrue-interest [:org date]` and `:capitalize-interest [:org date]` are now modelled (with `:run? false` so fugato doesn't generate them — interest needs date-keyed idempotency tracking before fugato gen makes sense). Products gained `:product-type` (`:settlement|:current|:savings`) and `:interest-rate-bps`; orgs gained `:settlement-account`. With this in place, `full-happy-path.edn` folds end-to-end through the model — the capstone scenario is now fully model-equal.
- [x] **6.4** Capstone: full UI happy-path encoded as `full-happy-path.edn`. 39 steps: org + settlement, two products (current + savings @ 3650 bps), Arthur + Ford as person-parties, four customer accounts, settlement funded with £50,000, party rewards, intra-party current↔savings moves, an outbound payment, daily accrual, monthly capitalisation. Asserts every key balance pre- and post-interest. The scenario landed two side findings:

  - The cash-account opening transition (`:opening` → `:opened`) goes through a changelog watcher in production. Without that watcher in the test system, freshly-opened accounts stay `:opening` and `bank-interest/accrue-daily` silently filters them out. Added `bank-cash-account/seed-opened-account` (test-bypass, parallels `bank-party/seed-active-party`) and the runner now flips status inline at open-time.
  - Custom products created via `:create-product` / `:create-savings-product` need `:balance-type-interest-accrued` and `:balance-type-interest-paid` in their `:balance-products` for accrual + capitalisation to land. Default-balance-products extended.

  Also clarified the capitalisation accounting: it's six legs, and the last pair credits `customer.default` with the accrued amount (the prior pairs unwind interest-accrued / interest-payable / interest-paid). Locking down the expected values surfaced this immediately.

Beyond this point, plan further work as separate tickets. The infrastructure is in place; new commands are additive.

## Out of scope for this plan

- **test.contract for ClearBank.** Separate piece of work; complementary, not blocking. See `docs/tdd/scenario-testing.md` "Considered alternatives" section.
- **Snapshot testing.** Reconsider once specific use cases (e.g. interest accrual ledger output) prove worth locking down.
- **Deterministic simulation.** The Allen Rohner / FoundationDB style of seeded-everything testing. Worth knowing about; not on this plan.

## Notes on working through this plan

- **One step per PR if practical.** Some steps in Phase 1 are small enough to combine. Phases 3–5 are not.
- **Don't skip Phase 4.** EDN scenarios in Phase 4 are easier to debug than fugato failures in Phase 5. If something is broken structurally, EDN scenarios will tell you in plain English; fugato will tell you with a shrunk sequence and a diff.
- **Resist enriching the model.** Throughout, the model stays pure-functional and ignorant of production infrastructure. If `bank-test-model` starts importing proto schemas or Malli contracts, push back.
- **Quiescence is real.** If tests start flaking after Phase 5, the answer is almost always quiescence-related, not test logic.
