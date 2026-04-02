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

echo "=== Restoring ours-always files from .gitattributes ==="
grep 'merge=ours-always' .gitattributes | awk '{print $1}' | while read -r pattern; do
  if [[ "$pattern" == *"*"* ]]; then
    # Expand globs via ls-tree for files that exist in HEAD
    git ls-tree -r --name-only HEAD | grep -E "^${pattern//\*\*/.*}" |
      xargs -r git checkout HEAD -- 2>/dev/null || true
  else
    git checkout HEAD -- "$pattern" 2>/dev/null || true
  fi
done

echo "=== Restoring queenswood-owned components/bases/projects ==="
git ls-tree -r --name-only HEAD |
  grep -E '^(components|bases|projects)/(bank-|queenswood)' |
  xargs -r git checkout HEAD --

echo "=== Removing upstream-added example files ==="
git ls-files | grep -E '(^|/)example[-_]' | xargs -r git rm -f
rm -rf components/example-* bases/example-* projects/example-*

git add -A

echo "=== Status after resolve ==="
git status --short

# git commit --no-edit
