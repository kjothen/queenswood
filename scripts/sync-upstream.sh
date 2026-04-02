#!/usr/bin/env bash
set -euo pipefail

git fetch upstream

set +e
git merge --no-commit --no-ff upstream/main
MERGE_EXIT=$?
set -e

echo "=== Unmerged bank- files (modify/delete conflicts) ==="
git ls-files -u | awk '{print $4}' | sort -u | grep -E '^(components|bases|projects)/bank-' || echo "NONE"

echo "=== Resolving conflicts in favour of HEAD ==="
git ls-files -u | awk '{print $4}' | sort -u |
  grep -E '^(components|bases|projects)/bank-' |
  xargs -r git add

git checkout HEAD -- workspace.edn deps.edn

rm -rf components/example-* bases/example-* projects/example-*
git add -A

echo "=== Status after resolve ==="
git status --short | head -30

# git commit --no-edit
