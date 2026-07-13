---
name: sync-rules-from-docs
description: |
  Regenerate a Tessl rule file's section bodies from the
  recipes/ADRs it links to, so the rule stays traceable to its
  source instead of drifting as an independently-paraphrased copy.
  Triggers on: "sync the idioms rule", "regenerate rules from docs",
  "check the rule against the docs", "did the recipe change break
  the rule". Defaults to plugins/idioms/rules/idioms.md; accepts
  another rule file's path.
license: Apache-2.0
allowed-tools: Bash Read Edit
metadata:
  version: '0.1'
  author: kjothen
  domain: software-engineering
  subdomain: docs-tooling
  tags: 'tessl, rules, docs, recipes, adr, generation'
---

# sync-rules-from-docs

A Tessl rule (always-loaded agent context) and its source docs
(`docs/recipes/*.md`, `docs/adr/*.md`) are two independent prose
statements of the same guidance. Left to drift, the rule can quietly
stop matching its source with no signal. This skill closes that gap:
it extracts the normative content straight from each section's linked
docs, then rewrites the rule section as a tight compression of *only*
that extracted material — no freely re-derived guidance, no invented
claims.

**Read the docs, write the rule** — never the other way around. If a
rule currently says something that doesn't trace back to any extracted
bullet, that's a finding to surface, not something to preserve.

## When to Use

After editing a recipe's `## Rules` block or an ADR's `## Decision`
section, or when asked to "sync the idioms rule" / "check the rule
against the docs". Also useful as a periodic sanity check even when
nothing's knowingly changed — it's cheap and it catches silent drift.

**Do not use** for authoring a *new* rule section from scratch (there's
nothing to extract yet — write the recipe/ADR first, then run this),
or for any docs outside `docs/recipes/` and `docs/adr/` (the extraction
shapes below are specific to those two).

## Workflow

1. **Run the extractor**: `bash plugins/workflow/skills/sync-rules-from-docs/extract.sh [rule-file]`
   (defaults to `plugins/idioms/rules/idioms.md`). This is a
   deterministic, judgment-free pass — it locates each rule section's
   trailing `See [...]` line, resolves every linked doc, and prints:
   - For a **recipe**: its `## Rules` section's `**MUST:**` /
     `**MUST NOT:**` bullets verbatim (and `**SHOULD:**` if present).
   - For an **ADR**: its `## Decision` section's lead paragraph, plus
     a colon-ending intro line and the list immediately following it,
     if that shape is present.

   If the extractor reports `ERROR: <doc> has no ## Rules section` (or
   `## Decision`), **stop for that section** — fix the doc's structure
   first, don't guess at what the rule should say. This is a real
   signal that the doc drifted out of the expected shape, not
   something to route around.

2. **For each section, compose the rule body from the extracted
   material only.** This is the one step that needs judgment — a
   readable rule can't be a raw bullet dump — but every factual claim
   in the composed prose (a function name, a forbidden call, a file
   name) must trace to something the extractor printed for that
   section. Two things the raw extraction doesn't resolve for you:
   - **ADR lists are ambiguous by shape.** "The rules: 1. …" (normative
     — belongs in the rule) and "Worked examples: - …" (illustrative —
     usually belongs left in the doc, not restated) look identical to
     the extractor. Read the extracted list and judge which kind it is;
     when a recipe is *also* linked for the same section, prefer its
     MUST/MUST NOT as the backbone and treat the ADR material as
     supporting color, not a second thing to restate in full.
   - **Compression, not concatenation.** Multiple MUST/MUST-NOT bullets
     from one or two docs should read as the rule's existing voice —
     short, imperative, "how to write" not "what's forbidden" — not as
     a pasted list. Match the surrounding sections' tone and length.

3. **Flag untraceable claims.** Before rewriting a section, diff its
   *current* body against the extracted material. If the current text
   asserts something with no matching bullet or Decision lead anywhere
   in the extraction, call it out explicitly in the report — don't
   silently drop it (it might be correct but the doc is missing it)
   and don't silently keep it (it might be stale). This is a decision
   for whoever reviews the diff, not for this skill to make
   unilaterally.

4. **Leave the section heading and the trailing `See [...]` line
   untouched.** They're already the pointer to source; only the body
   paragraph between them gets rewritten.

5. **Write the file.** No interactive confirmation gate — this
   produces a normal git diff for review via the usual PR flow, the
   same way `new-processor` and `commit-and-pr` operate in this repo.
   Don't run `git commit` yourself; that's a separate, explicit step.

## Output

Report, per section: which doc(s) it extracted from, a short summary
of what changed (or "unchanged" if the compressed prose already
matched), and any untraceable claims found. If a section's source doc
had a structural `ERROR`, report that and leave the section as-is.

Example:

```
Return anomalies, don't throw across a boundary
  Sources: docs/recipes/error-handling.md, ADR-0005
  Changed: added "anomaly category names the call site" (was missing)
  Untraceable: none

Comment the why, not the what
  Sources: ADR-0015
  Changed: unchanged
  Untraceable: none
```
