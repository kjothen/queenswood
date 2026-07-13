# Queenswood dev workflow

Project-specific conventions on top of standard git practice — keeping
branches current against Renovate, and the local pre-commit gate.

## Pull from `main` before committing; let Renovate own dependency bumps

Pull and merge from `main` before committing — Renovate auto-merges
dependency updates weekly, so a branch that hasn't pulled is likely
already behind — and resolve any conflict with a Renovate-managed
file (`deps.edn`, `.github/workflows/*`) before pushing. Never
manually bump a dependency version Renovate manages, except when a
real need requires a version Renovate hasn't yet caught up to. Stage
a user-initiated deletion or move with `git add` (`-u` or `-A`),
never `git rm` — reserve `git rm` for a deletion you are yourself
initiating. Never develop new features inside the user's untracked
drafts; do include them in workspace-wide operations (rename,
dead-code cleanup, regeneration) so the tree stays consistent, and
report what was touched.
See [git-workflow](../../../docs/recipes/git-workflow.md).

## The pre-commit hook formats and lints before CI sees it

A checked-in pre-commit hook at `scripts/hooks/pre-commit` does two
jobs on every commit: formats staged Clojure files with zprint
(auto-fix, restaged, configured by `.zprint.edn` at 80-column width),
and lints with clj-kondo against the full `bases`, `components`, and
`projects` trees whenever any Clojure file is staged — lint errors
block the commit, configured by `.clj-kondo/config.edn` including
`lint-as` mappings for the project's macros.
See [ADR-0012](../../../docs/adr/0012-pre-commit-hooks.md).
