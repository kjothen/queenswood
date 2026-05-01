# Git workflow

## Problem

You want to commit changes or push to your branch.

## Solution

Two project-specific rules sit on top of standard git practice:

1. **Merge from `main` before committing.** Renovate
   auto-merges dependency updates to `main` on a weekly
   schedule, so a feature branch that hasn't pulled recently
   is likely behind.
2. **Don't manually bump dependency versions Renovate
   manages.** Renovate (configured in `renovate.json`) handles
   all Clojure `deps.edn` and GitHub Actions version bumps.
   Manual bumps fight the next Renovate PR.

### Merging from main

Before committing on a branch (or on `main` directly):

```bash
git fetch origin
git merge origin/main
```

If Renovate merged a `deps.edn` change while you were working,
this pulls in the updated versions. Resolve any conflicts and
re-run tests before continuing.

If you've made local changes to a file Renovate also touched
(`deps.edn`, `.github/workflows/*`), the merge conflict is
real and needs human resolution.

### What Renovate manages

`renovate.json` configures the bot to update:

- Clojure dependencies in `deps.edn` files (workspace root,
  bricks, projects).
- GitHub Actions versions in `.github/workflows/*`.
- Other ecosystems as configured.

Renovate runs on a weekly schedule and opens PRs that
auto-merge once CI passes.

### When you genuinely need a version bump

If you need a dependency version Renovate hasn't yet picked
up (security fix, runtime bug fix, a transitive incompatibility
you've just discovered):

1. Check whether a Renovate PR is already open for it.
2. If you must bump manually, keep the commit narrowly scoped
   so the next Renovate PR can either close (if you've already
   landed the same bump) or merge cleanly (if Renovate goes
   higher).

## Rules

**MUST:**

- Pull/merge from `main` before committing.
- Resolve conflicts with Renovate-managed files (`deps.edn`,
  `.github/workflows/*`) before pushing.

**MUST NOT:**

- Manually bump dependency versions Renovate manages, except
  when a real need requires a version Renovate hasn't yet
  caught up to.

## Discussion

Renovate exists so dependency upkeep doesn't fall on humans.
The trade-off is that branches go out of date faster — a week
of feature work can be sitting behind several merged Renovate
PRs. Pulling `main` before committing keeps the gap small and
catches conflicts early.

The "don't manually bump" rule is about avoiding two PRs
fighting for the same `deps.edn` line. Renovate's PR will
conflict with a manual bump and either auto-close or fail to
merge; either way it's churn. Let Renovate do its job.

If a build genuinely needs a newer version than Renovate has
picked up, bumping manually is fine — just keep the commit
narrow so the bot's next PR can reconcile cleanly.

## References

- `renovate.json` (workspace root)
- [Renovate documentation](https://docs.renovatebot.com/)
