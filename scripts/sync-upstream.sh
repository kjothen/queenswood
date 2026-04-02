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

echo "=== Restoring all queenswood-owned files from HEAD ==="
git ls-tree -r --name-only HEAD |
  grep -E '^(components|bases|projects)/(bank-|queenswood)' |
  xargs -r git checkout HEAD --

git ls-tree -r --name-only HEAD |
  grep -E '^(development|scripts)/' |
  xargs -r git checkout HEAD --

git checkout HEAD -- workspace.edn deps.edn .clj-kondo/config.edn
git checkout HEAD -- .github/workflows/

echo "=== Removing example-* ==="
rm -rf components/example-* bases/example-* projects/example-*
git add -A

echo "=== Status after resolve ==="
git status --short

# git commit --no-edit
