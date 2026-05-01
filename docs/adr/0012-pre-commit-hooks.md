# 12. Pre-commit hooks for formatting and linting

## Status

Accepted.

## Context

A Clojure codebase produces a steady supply of formatting drift and
lint issues. By the time CI catches them, the contributor has
already pushed; the feedback loop is minutes long and the diff
reviewers see is noisier than it needs to be.

We want fast local feedback for the cheap, mechanical issues —
formatting consistency and lint errors — so they never reach the
review stage.

The shortlist:

- **CI-only.** Catches everything, but the feedback loop is the
  full CI cycle. Cheap issues become expensive.
- **Editor-only.** Format-on-save in the contributor's editor.
  Works, but depends on each contributor's editor configuration;
  drift between editors leaves formatting inconsistent.
- **JavaScript-ecosystem hook managers** (husky, lefthook). Pull
  Node tooling into a Clojure project. Off-stack.
- **A checked-in shell script installed as a `git` pre-commit
  hook.** Fast local feedback, no extra tooling, the hook lives in
  the repo and runs the same way for everyone who installs it.

## Decision

We will keep a pre-commit hook at `scripts/hooks/pre-commit`,
checked in to the repo. The hook does two jobs:

- **Format staged Clojure files with zprint.** Auto-fix; the
  reformatted files are restaged. Configured by `.zprint.edn`
  (80-column width).
- **Lint with clj-kondo** against the full `bases`, `components`,
  and `projects` trees whenever any Clojure file is staged. Lint
  errors block the commit. Configured by `.clj-kondo/config.edn`,
  including `lint-as` mappings for the project's macros.

Installation is manual, once per clone:

```
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

The hook is a local convenience. The actual gate is CI, which runs
the same checks. Bypassing the hook (`git commit --no-verify`)
produces a commit that CI will reject if it has formatting or lint
issues.

## Consequences

Easier:

- Fast feedback. Formatting drift and lint errors are caught
  before commit, not minutes later in CI.
- Reviews focus on the change. Mechanical issues don't reach the
  review stage.
- The codebase stays consistently formatted because zprint runs on
  every commit, not on every contributor's whim.
- The hook is checked in. Everyone who installs it runs the same
  script with the same configurations.

Harder:

- Installation is a manual step per clone. Easy to forget;
  documented in CLAUDE.md. The cost of forgetting is "CI fails on
  the next push," not silent breakage.
- clj-kondo runs against the full workspace, not just staged
  files. This is deliberate — lint can fail on indirect breakage —
  but it means commit time grows with workspace size. So far this
  is fine.
- Hooks are bypassable (`--no-verify`). The hook is a discipline,
  not enforcement. CI is the gate.
- The hook depends on `zprint` and `clj-kondo` being available
  locally. Both come in via project tooling, but a fresh setup
  needs both before the hook is useful.
