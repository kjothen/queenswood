---
name: commit-and-pr
description: Commit everything in the working tree with a drafted message, push to origin, and open a PR — end to end, no approval pauses. Stops only if a secret/credential file is about to be staged.
allowed-tools: Bash
---

# commit-and-pr

Compresses the commit → push → PR flow into one
invocation and runs it through without stopping. Draft the
commit message and PR body, commit everything, push, open
the PR, and report what landed — don't pause for approval.
The user reviews after the fact (and on the PR itself).

The only hard stop is the secret/noise check in step 2 —
that's a safety net, not an approval gate.

## Current state

!`git rev-parse --abbrev-ref HEAD`
!`git status --short`
!`git diff --stat HEAD`
!`git log --oneline -8`

## Workflow

Run all of these in sequence without pausing between them.

1. **Survey every change in the working tree.** Read
   modified and untracked files as needed to understand
   intent. The point is to commit *everything* in the
   working tree — including changes made in parallel
   sessions (Claude Desktop, manual edits, other tools) —
   not just files this Claude Code session knows it
   touched. The diff is the source of truth, not the
   conversation history.
2. **Secret/noise check — the one hard stop.** If any
   change matches `.env*`, `credentials*`, `*.key`,
   `*.pem`, `*.pfx`, `*.p12`, STOP and ask before going
   further. Also call out (but don't stop for) anything
   that looks unintentional — large binaries, build
   artefacts, IDE droppings, files in
   gitignored-but-tracked-anyway paths. If nothing trips
   this, continue straight through to the end.
3. **Draft a commit message from the diff.** Title
   imperative, short (≤ 70 chars). Body paragraph for
   non-trivial changes, focusing on *why* not *what*. The
   message must reflect the full diff, not just what we
   did together this session — read the changed files if
   needed to understand parallel work. End with the
   `Claude-Session:` trailer your environment specifies.
   Its URL is per-session, so take it from the environment
   rather than copying one from an earlier commit. No
   `Co-Authored-By` trailer. Match the prevailing style in
   `git log` above.
4. **Stage, commit, push — no pause.** Stage everything
   with `git add -A` (the step-2 check already cleared
   secrets/noise). Commit with `git commit -m` via
   heredoc. Push with `git push -u origin HEAD` —
   **always**, never plain `git push`. If a pre-commit hook
   fails, fix the cause, re-stage, and commit again (a NEW
   commit — never `--amend`).

   Work belongs on a remote branch of the same name as the
   local one, which is what `-u origin HEAD` guarantees.
   Don't reach for plain `git push` on the grounds that an
   upstream already exists: `fresh-branch` cuts branches
   with `git checkout -b <name> origin/main`, which sets
   the upstream to **`origin/main`**, not to a branch of
   the same name. So a tracking branch is configured, it is
   the wrong one, and a bare `git push` aims the work at
   `main`. `-u origin HEAD` creates or updates the
   same-named remote branch and repoints the upstream at
   it.
5. **Draft the PR body and open it — no pause.** Title
   from the commit title (or an umbrella description for
   multi-commit PRs). Open immediately with `gh pr create`
   (base `main`) using a heredoc body:

   ```
   ## Summary
   <1-3 bullets capturing the why>

   ## Test plan
   <bulleted checklist for the reviewer>

   🤖 Generated with [Claude Code](https://claude.com/claude-code)
   ```
6. **Report.** Show the commit message you used and return
   the PR URL, so the user can review what landed.

## Guardrails

- Never `--amend`. If a pre-commit hook fails, fix the
  issue, re-stage, and create a NEW commit.
- Never force-push to `main` / `master`. Refuse if asked.
- If the current branch IS `main` / `master`, refuse and
  ask which branch to push to. (Worktree-based work is
  rarely on `main`, so this seldom fires.)
- Push to a same-named remote branch, always via
  `git push -u origin HEAD`. A configured upstream is not
  evidence it points at the right branch — see step 4.
- Before opening the PR, confirm `gh pr view` reports the
  head as the local branch and the base as `main`. A PR
  whose head is `main` means the push went astray.
- Don't skip hooks (`--no-verify`) unless explicitly
  asked.

The full Git Safety Protocol lives in
[CLAUDE.md](../../../CLAUDE.md).
