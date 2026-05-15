---
name: bank-guardrails
description: Scan the current branch's Clojure changes for Queenswood critical-guardrail violations — throws from interface.clj, raw random-uuid / Instant/now / System/currentTimeMillis, use-fixtures in tests, cross-unit internal imports past .interface (components and bases), and oversized comment blocks — and report findings against CLAUDE.md. Use as a pre-commit / pre-PR check ("check guardrails", "lint bank rules", "did I break any conventions?", "ready to commit?", "scan for guardrail violations").
allowed-tools: Bash
---

# bank-guardrails

Runs every guardrail listed under
[Critical guardrails](../../../CLAUDE.md#critical-guardrails) in
CLAUDE.md across the changed Clojure code and reports findings.

CLAUDE.md and the linked recipes/ADRs are the source of truth for
*what's wrong* and *how to fix it*. This skill is the runner.

## Scope

Default scope: files changed on the current branch (commits ahead
of `main`, plus working-tree modifications and untracked files).
This matches the pre-commit / pre-PR use case.

Overrides:

- `bash .claude/skills/bank-guardrails/checks.sh --staged` —
  only staged changes
- `bash .claude/skills/bank-guardrails/checks.sh --all` — every
  tracked Clojure file in the repo
- `bash .claude/skills/bank-guardrails/checks.sh <path> ...` —
  explicit paths

Only `.clj`, `.cljc`, and `deps.edn` files are scanned; everything
else is skipped.

## Guardrails checked

| Check | What it flags | Source |
|-------|---------------|--------|
| `throw-from-interface` | `throw`, `ex-info`, or `ex-cause` inside any `interface.clj` | [recipes/error-handling.md](../../../docs/recipes/error-handling.md), [ADR-0005](../../../docs/adr/0005-error-handling-with-anomalies.md) |
| `raw-time-id` | `(random-uuid)`, `(UUID/randomUUID)`, `(Instant/now)`, `(System/currentTimeMillis)` outside `components/utility/` | [recipes/common-helpers.md](../../../docs/recipes/common-helpers.md) |
| `use-fixtures-in-tests` | `use-fixtures` in any `*_test.clj` | [recipes/testing.md](../../../docs/recipes/testing.md) |
| `cross-unit-internal` | for component sources, a require of `com.repldriven.mono.<other-component>.<not-interface/system>`; for base sources, the same rule plus the carve-out that base→base imports are only allowed when targeting `.system` | [recipes/components.md](../../../docs/recipes/components.md), [recipes/bases.md](../../../docs/recipes/bases.md), [ADR-0011](../../../docs/adr/0011-one-component-per-third-party-library.md) |
| `comment-block-bloat` | runs of 5+ consecutive `;`-comment lines in source | [ADR-0015](../../../docs/adr/0015-comments-and-docstrings.md) |

## What to do with the findings

Treat every `PASS` line as a clean check. For each `FAIL`:

1. Open the source doc from the table above; it explains the
   why and the "OK" pattern.
2. Apply the fix that follows the recipe — don't restate the
   recipe in the response.
3. Re-run the skill to confirm.

Per-check fix hints, in order of typical fix:

- **`throw-from-interface`** — wrap the offending body in
  `error/try-nom` (or `error/try-nom-ex` if you need the
  exception object) and return an anomaly map. Never raise
  across the interface boundary.
- **`raw-time-id`** — replace with `util/uuidv7` for IDs and
  `util/now` for timestamps. Add
  `[com.repldriven.mono.utility.interface :as util]` to the
  requires if missing.
- **`use-fixtures-in-tests`** — replace with `with-test-system`
  and assert anomaly-freeness with `nom-test>`.
- **`cross-unit-internal`** — change the require to the other
  unit's `interface` namespace (or `.system` if the carve-out
  applies). If the symbol you need isn't on the interface, add
  it there first, then call it. Never reach into `.core` /
  `.store` / `.domain` / etc. of another component or base.
- **`comment-block-bloat`** — the block is a candidate for
  pruning to the load-bearing *why* (invariant, workaround,
  upstream constraint) or promotion to a `defn` docstring. If
  the block genuinely captures non-obvious context that isn't
  visible from the code, leave it.

The `comment-block-bloat` check is a heuristic — a long block
can be legitimate if it captures real why-state. Skim before
deleting.

## Reporting

Report results in this shape:

- One line per check: `throw-from-interface: 0`,
  `raw-time-id: 4 (in bank-cash-account-product,
  bank-organization, bank-api-key, bank-idv)`, …
- For each non-empty check, the file:line refs from the script's
  output and a per-item suggested fix from the hints above.
- Stop once everything's clean.

If everything passes, say so in one line.

## Editing the script

The actual checks live in `checks.sh` in this skill's directory.
Edit it to add a guardrail, tighten a regex, or adjust the file
scope. Keep new checks in the `section` + `report` shape so the
output format stays consistent across runs.
