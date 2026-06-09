---
name: fresh-branch
description: Fetch origin and cut a new branch off the latest origin/main. Use after merging a PR via the GitHub UI to start fresh on the next piece of work. Worktree-safe. Pass the new branch name as the argument.
allowed-tools: Bash
---

# fresh-branch

Used after merging a PR via the GitHub UI: sync with
`origin` and cut a new branch off the latest `origin/main`
for the next piece of work.

Works in both a primary checkout and a linked worktree. It
never checks out `main` locally, so it doesn't trip over
`main` being checked out in another worktree (git refuses
to check out the same branch twice).

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
2. **Verify `$ARGUMENTS` is set and is a bare branch
   name.** If it's empty, refuse and ask for one. If it
   carries extra prose (a sentence, a trailing question),
   don't guess — take the intended branch name as the
   first token and confirm it with the user before
   cutting.
3. **Verify the branch doesn't already exist** (locally
   or on origin).

   !`if git rev-parse --verify --quiet refs/heads/$ARGUMENTS >/dev/null; then echo "EXISTS (local)"; elif git ls-remote --exit-code --heads origin $ARGUMENTS >/dev/null 2>&1; then echo "EXISTS (origin)"; else echo NEW; fi`

   If it `EXISTS`, refuse and ask whether to switch to it
   or pick a different name.
4. **Fetch and cut the branch off the latest
   `origin/main`.**

   ```
   git fetch origin --prune
   git checkout -b $ARGUMENTS origin/main
   ```

   This branches directly off the freshly-fetched remote
   tip, so it's correct whether you're in the primary
   checkout or a linked worktree, and it leaves the old
   branch's commits untouched. There's no local `main` to
   fast-forward and nothing to merge, so the old
   `pull --ff-only` divergence case can't arise.

   Optionally keep the local `main` ref current too (purely
   cosmetic — the new branch already points at the remote
   tip):

   ```
   git fetch origin main:main
   ```

   This updates the ref without checking it out, and fails
   harmlessly if `main` is checked out in another worktree
   — ignore that error if it appears.
5. **Confirm.**

   !`git rev-parse --abbrev-ref HEAD`
   !`git log --oneline -3`

## Guardrails

- Don't stash, reset, or discard uncommitted changes.
- Never check out `main` directly — branch off
  `origin/main`. This is what keeps the skill worktree-safe.
- Don't overwrite an existing local or remote branch.
