---
name: commit-and-pr
description: Commit current changes with a drafted message, push to origin, and open a PR. Pauses after drafting so you can sanity-check the commit message and PR body before either lands.
allowed-tools: Bash
---

# commit-and-pr

Compresses the commit → push → PR flow into one
invocation. Pauses after drafting so you can sanity-check
the commit message and PR body before either lands.

## Current state

!`git rev-parse --abbrev-ref HEAD`
!`git status --short`
!`git diff --stat HEAD`
!`git log --oneline -8`

## Workflow

1. **Survey every change in the working tree.** Read
   modified and untracked files as needed to understand
   intent. The point is to commit *everything* in the
   working tree — including changes made in parallel
   sessions (Claude Desktop, manual edits, other tools)
   — not just files this Claude Code session knows it
   touched. The diff is the source of truth, not the
   conversation history.
2. **Filter for secrets and noise.** Refuse to stage
   anything matching `.env*`, `credentials*`, `*.key`,
   `*.pem`, `*.pfx`, `*.p12`. If any are present, flag
   them and ask before proceeding. Also flag (but don't
   refuse) anything that *looks* unintentional — large
   binaries, build artefacts, IDE droppings, files in
   gitignored-but-tracked-anyway paths.
3. **Draft a commit message from the diff.** Title
   imperative, short (≤ 70 chars). Body paragraph for
   non-trivial changes, focusing on *why* not *what*.
   The message must reflect the full diff, not just what
   we did together this session — read the changed files
   if needed to understand parallel work. Trailer:
   `Co-Authored-By: Claude Opus 4.7 (1M context)
   <noreply@anthropic.com>`. Match the prevailing style
   in `git log` above.
4. **Show me the drafted message and the file list you
   plan to stage.** Wait for "go" or adjustments before
   committing.
5. **Stage, commit, push.** Stage by explicit paths
   (`git add path1 path2 ...`) rather than `-A` —
   listing the paths gives a final visual check against
   the secret/noise filter from step 2. Commit with
   `git commit -m` via heredoc. Push with
   `git push -u origin HEAD` if there's no upstream
   (otherwise plain `git push`).
5. **Draft the PR.** Title from the commit title (or an
   umbrella description for multi-commit PRs). Body:

   ```
   ## Summary
   <1-3 bullets capturing the why>

   ## Test plan
   <bulleted checklist for the reviewer>

   🤖 Generated with [Claude Code](https://claude.com/claude-code)
   ```

   Show me before opening.
6. **Open with `gh pr create`** using a heredoc body.
   Return the PR URL.

## Guardrails

- Never `--amend`. If a pre-commit hook fails, fix the
  issue, re-stage, and create a NEW commit.
- Never force-push to `main` / `master`. Refuse if asked.
- If the current branch IS `main` / `master`, refuse and
  ask which branch to push to.
- Don't skip hooks (`--no-verify`) unless explicitly
  asked.

The full Git Safety Protocol lives in
[CLAUDE.md](../../../CLAUDE.md).
