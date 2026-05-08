---
name: fresh-branch
description: Switch to main, fetch, fast-forward pull, and create a new branch. Use after merging a PR via the GitHub UI to start fresh on the next piece of work. Pass the new branch name as the argument.
allowed-tools: Bash
---

# fresh-branch

Used after merging a PR via the GitHub UI: switch back to
`main`, sync with `origin`, and cut a new branch for the
next piece of work.

Argument: `$ARGUMENTS` is the new branch name (e.g.
`scenario-testing-ftw`).

## Current state

!`git rev-parse --abbrev-ref HEAD`
!`git status --short`

## Steps

1. **Verify clean state.** If the `git status --short`
   above shows anything (modified, staged, untracked),
   stop and ask. Don't stash, reset, or discard — the
   user may have work-in-progress that matters.
2. **Verify `$ARGUMENTS` is set.** If it's empty, refuse
   and ask for a branch name.
3. **Verify the branch doesn't already exist.**

   !`git rev-parse --verify --quiet refs/heads/$ARGUMENTS && echo EXISTS || echo NEW`

   If `EXISTS`, refuse and ask whether to switch to it
   or pick a different name.
4. **Switch, fetch, fast-forward pull, branch.**

   ```
   git checkout main
   git fetch origin --prune
   git pull --ff-only origin main
   git checkout -b $ARGUMENTS
   ```

   `--ff-only` refuses if `main` has diverged. If it
   refuses, stop and report — don't fall back to merge
   or rebase without asking.
5. **Confirm.**

   !`git rev-parse --abbrev-ref HEAD`
   !`git log --oneline -3`

## Guardrails

- Don't stash, reset, or discard uncommitted changes.
- Don't merge or rebase if `pull --ff-only` refuses;
  surface the divergence and ask.
- Don't overwrite an existing local branch.
