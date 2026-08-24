---
name: check-docs
description: Run all doc-quality checks (wrap, mermaid, link hygiene, vocabulary) across docs/, readme.md, and CLAUDE.md, and report violations against the writing-docs recipe.
allowed-tools: Bash
---

# check-docs

Runs every verification command from
[docs/recipes/practices/writing-docs.md](../../../docs/recipes/practices/writing-docs.md)
across the project's markdown corpus and reports findings.

The recipe is the source of truth for *what's wrong* and
*how to fix it*. This skill is the runner.

## Scope

- Every `*.md` under `docs/`, except `docs/slides/`
  (slidev presentations) and `docs/plan/` (in-flight
  plans, often containing quoted REPL output).
- `readme.md` and `CLAUDE.md` at the repo root.
- `docs/recipes/practices/writing-docs.md` itself is excluded from
  *content-pattern* checks (paren-adjacent links, code-as-
  link-text, maturity overclaim, competitor names,
  brittle-temporal) because it documents those patterns
  by design with "Bad" / "OK" examples. It is still
  subject to wrap and mermaid checks.

## Findings

!`bash .claude/skills/check-docs/checks.sh`

## What to do with the findings

Treat every section that says `PASS` as a clean check.

For each `FAIL` section:

1. Map the file:line reference to the relevant rule in
   [docs/recipes/practices/writing-docs.md](../../../docs/recipes/practices/writing-docs.md).
   The rules and their rationales live there; don't
   restate them, just follow them.
2. Suggest a fix that follows the recipe's "OK" pattern,
   not its "Bad" pattern.
3. For PRD vocabulary findings, prefer the product
   register (background, automatically, in one go) over
   the engineering register. The recipe lists the avoid /
   prefer pairs.
4. For inline-code-as-link-text findings, the standard
   exception is library citations (`[`donut.system`](url)`,
   `[`de.otto/nom`](url)`, etc.). These are conventional
   in the existing recipes and ADRs and are likely
   intentional; flag them but don't suggest changing them
   unless the user asks.
5. For wrap findings, check whether the long line is
   inside a markdown table (rows can't easily wrap) or an
   image link with a long URL — those are usually
   acceptable. Otherwise, rewrap to ≤ 80.
6. For paren-adjacent-link findings, restructure with em-
   dash or comma so no `)` sits adjacent to a link's `)`.
7. For brittle-temporal findings, replace counts /
   datestamps / "recently" with timeless framings ("each",
   "the relevant", drop the count).

Report a summary in this shape:

- One line per check: `wrap: 0`, `mermaid-semicolon: 0`,
  `paren-adjacent: 1 in tdd/idempotency.md`, …
- Then, for each non-empty check, the file:line refs and
  a suggested fix per item.
- Stop once everything's clean.

If everything passes, say so in one line and stop.

## Editing the script

The actual checks live in `checks.sh` in this skill's
directory. Edit it to add a check, tighten a regex, or
adjust the file scope. Keep new checks structured the
same way (`section` + `report` calls) so the output
format stays consistent.
