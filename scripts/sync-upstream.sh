#!/usr/bin/env bash
set -euo pipefail

git fetch upstream

set +e
git merge --no-commit --no-ff upstream/main
MERGE_EXIT=$?
set -e

echo "=== Resolving modify/delete conflicts in favour of HEAD ==="
git ls-files -u | awk '{print $4}' | sort -u |
  grep -E '^(components|bases|projects)/bank-' |
  xargs -r git add

echo "=== Restoring cleanly deleted bank- files ==="
git diff --cached --name-only --diff-filter=D |
  grep -E '^(components|bases|projects)/bank-' |
  xargs -r git checkout HEAD --

echo "=== Restoring queenswood-owned files ==="
git checkout HEAD -- workspace.edn deps.edn

echo "=== Removing example-* ==="
rm -rf components/example-* bases/example-* projects/example-*
git add -A

echo "=== Status after resolve ==="
git status --short

# git commit --no-edit
