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
   Co-Authored-By trailer your environment / CLAUDE.md
   specifies (currently
   `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`).
   Match the prevailing style in `git log` above.
4. **Stage, commit, push — no pause.** Stage everything
   with `git add -A` (the step-2 check already cleared
   secrets/noise). Commit with `git commit -m` via
   heredoc. Push with `git push -u origin HEAD` when there
   is no upstream, otherwise plain `git push`. If a
   pre-commit hook fails, fix the cause, re-stage, and
   commit again (a NEW commit — never `--amend`).
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
- Don't skip hooks (`--no-verify`) unless explicitly
  asked.

The full Git Safety Protocol lives in
[CLAUDE.md](../../../CLAUDE.md).
