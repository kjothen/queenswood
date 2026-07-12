# Guardrail rules — detail

The five critical guardrails in full: the rationale, the source doc
that owns each rule, and the carve-outs/heuristics. `SKILL.md` carries
the compact operational table; load this when you need the *why* or an
edge case.

## 1. unmarked-throw

**Flag:** a `(throw …)` form that has no `;; nosemgrep: no-raw-throw`
opt-out comment on the line above it (or on the same line).
**Why:** exceptions must not escape a brick boundary — an interface
returns a value or an anomaly, never raises (ADR-0005,
recipes/error-handling.md). Because a throw *anywhere* in the call
graph can leak out, and that is undetectable statically in a dynamic
language, the invariant is enforced by inverting it: every throw is
flagged (the `no-raw-throw` semgrep rule in `.semgrep.yml`, run by the
pre-commit hook), and each genuinely-required one carries an explicit
opt-out marker. A new *unmarked* throw is therefore either a boundary
leak or a legitimate throw missing its marker.
**Fix:** prefer converting it — `error/reject` for a domain rejection,
`error/try-nom` / `error/nom->` at a library edge — so no exception
escapes. If the throw is genuinely required (a startup fatal, an
`InterruptedException` rethrow, a boundary conversion, or test code),
add `;; nosemgrep: no-raw-throw` on the line above it. The existing
grandfathered throws are already marked; this rule catches *new*
unmarked ones.

## 2. raw-time-id

**Flag:** `(random-uuid)`, `(UUID/randomUUID)`, `(Instant/now)`, or
`(System/currentTimeMillis)` anywhere **outside `components/utility/`**.
**Why:** IDs and timestamps come from the `utility` brick so they are
uniform and mockable. (recipes/common-helpers.md)
**Fix:** use `util/uuidv7` for IDs and `util/now` for timestamps,
adding `[com.repldriven.mono.utility.interface :as util]` to the
requires if missing. The only place the raw primitives are called is
`components/utility/` itself.

## 3. use-fixtures-in-tests

**Flag:** `use-fixtures` in any `*_test.clj`.
**Why:** test lifecycle is managed with `with-test-system`, not
fixtures. (recipes/testing.md)
**Fix:** replace with `with-test-system` for setup/teardown and assert
anomaly-freeness with `nom-test>`.

## 4. cross-unit-internal

**Flag:** a `require` of another unit's internal namespace — i.e.
`com.repldriven.mono.<other-unit>.<segment>` where `<segment>` is
**not** `interface` (and not `system`). Applies to sources under both
`components/` and `bases/`.
**Carve-out:** a base may require another base's `.system` namespace
(bare-require bundles); that is allowed.
**Why:** cross brick boundaries only through `interface.clj` (or
`.system` for registration). Reaching into `.core` / `.store` /
`.domain` / etc. of another unit breaks the boundary.
(recipes/components.md, recipes/bases.md, ADR-0011)
**Fix:** change the require to the other unit's `interface` namespace.
If the symbol you need is not exposed there, add it to that interface
first, then call it.

## 5. comment-block-bloat

**Flag:** a run of **5 or more consecutive** `;`-comment lines in a
source file.
**Why:** docstrings on `interface.clj` are the documentation surface;
impl files stay bare, and inline comments capture only the
load-bearing *why*. (ADR-0015)
**Fix:** prune the block to the load-bearing *why* (an invariant,
workaround, or upstream constraint) or promote it to a `defn`
docstring. This is a **heuristic** — a long block that genuinely
captures non-obvious why-state is acceptable; skim before flagging,
and mark such a case as a soft finding.
