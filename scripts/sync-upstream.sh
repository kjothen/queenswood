# scripts/sync-upstream.sh
#!/usr/bin/env bash
set -euo pipefail

git fetch upstream
git merge --no-commit --no-ff upstream/main

# Restore queenswood-specific files from our pre-merge HEAD
git ls-tree -r --name-only HEAD |
  grep -E '^(components|bases|projects)/bank-' |
  xargs git checkout HEAD --

git checkout HEAD -- workspace.edn deps.edn

# Remove anything example-* upstream added
rm -rf components/example-* bases/example-* projects/example-*
git add -A

# git commit --no-edit
