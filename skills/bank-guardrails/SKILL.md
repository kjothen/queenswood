---
name: bank-guardrails
description: |
  Use before committing or opening a PR on Queenswood Clojure changes
  to catch critical-guardrail violations. Triggers on: "check
  guardrails", "lint bank rules", "did I break any conventions",
  "ready to commit", "scan for guardrail violations". Flags unmarked
  raw throws (missing the no-raw-throw opt-out), raw random-uuid /
  Instant/now / System/currentTimeMillis outside utility, use-fixtures
  in tests, cross-unit internal imports past .interface, and oversized
  comment blocks — reporting each against the CLAUDE.md rule it breaks.
license: Apache-2.0
metadata:
  version: '0.1'
  author: kjothen
  domain: software-engineering
  subdomain: code-conventions
  tags: 'clojure, polylith, code-review, conventions, pre-commit'
---

# bank-guardrails

Scan the Clojure changed on the current branch for the five
critical-guardrail violations that Queenswood's CLAUDE.md forbids, and
report each against the rule it breaks with a concrete fix.
**Read-only**: identify and explain violations; do not edit code.

CLAUDE.md's "Critical guardrails" section and its linked recipes/ADRs
are the source of truth for *what's wrong* and *how to fix it*. This
skill is the reviewer that applies them to a diff.

## When to Use

Use as a pre-commit / pre-PR gate on a branch of Clojure changes, or
whenever asked to "check guardrails" / "did I break any conventions".
It answers one question: *does the changed code violate any of the
five load-bearing rules below?*

**Do not use** for: general code review or style nits beyond these
five rules; the processor-brick invariants (`check-processors`);
doc-quality checks (`check-docs`); or running the project's tests.
This skill neither builds nor runs code.

## Scope

Review only the files **changed on the current branch** — the union
of commits ahead of `main`, unstaged working-tree changes, and
untracked files:

```bash
{ git diff --name-only "$(git merge-base HEAD main)"..HEAD
  git diff --name-only
  git ls-files --others --exclude-standard ; } | sort -u
```

Keep only `.clj`, `.cljc`, and `deps.edn` files that still exist on
disk; skip everything else. If the user names explicit paths, review
those instead.

## Guardrails

Apply all five to every in-scope file. Flag only genuine matches, and
name the rule + source in each finding. The exact shapes are below;
the rationale, source docs, and carve-outs are in
[references/guardrails.md](references/guardrails.md) — read it for the
*why* or an edge case.

| # | Rule | Flag (exact shape) | Fix |
|---|------|--------------------|-----|
| 1 | unmarked-throw | a `(throw …)` with no `;; nosemgrep: no-raw-throw` opt-out on the line above (or same line) | convert to an anomaly (`error/reject` for a domain rejection, `error/try-nom` at a library edge), or add the opt-out marker if the throw is genuinely required |
| 2 | raw-time-id | `(random-uuid)` / `(UUID/randomUUID)` / `(Instant/now)` / `(System/currentTimeMillis)` outside `components/utility/` | `util/uuidv7` for IDs, `util/now` for timestamps |
| 3 | use-fixtures-in-tests | `use-fixtures` in any `*_test.clj` | `with-test-system` + `nom-test>` |
| 4 | cross-unit-internal | require of `com.repldriven.mono.<other-unit>.<seg>` where `<seg>` is not `interface`/`system` (base→base `.system` is allowed) | require the unit's `.interface` (add the symbol there if missing) |
| 5 | comment-block-bloat | 5+ consecutive `;`-comment lines (heuristic → soft finding) | prune to the load-bearing *why*, or promote to a docstring |

## Workflow

1. **Resolve scope** (above); filter to `.clj` / `.cljc` / `deps.edn`.
   If the set is empty, report that and stop.
2. **Scan each file against all five rules.** Rules 1–4 have exact
   textual shapes — match precisely, don't guess. Use judgement only
   on rule 5 (comment bloat) and the rule-4 carve-out.
3. **Record each violation** as `(rule, file:line, snippet)`.
4. **Report** in the format below.

Deterministic companions cross-check this review: the
`no-raw-throw` semgrep rule (`.semgrep.yml`, run by the pre-commit
hook) enforces rule 1 repo-wide, and
`.claude/skills/bank-guardrails/checks.sh` implements rules 2–5 as
bash regexes.

## Output

Lead with a one-line-per-rule summary, then details for any rule that
fired:

```
throw-from-interface: 0
raw-time-id: 2 (bank-idv, bank-payment)
use-fixtures-in-tests: 0
cross-unit-internal: 0
comment-block-bloat: 1 (soft)
```

For each non-zero rule, list the `file:line`, the offending snippet,
and the one-line fix — do not restate the recipe. If every rule is
`0`, say so in one line and stop. Report only; never edit code.
