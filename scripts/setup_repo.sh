#!/usr/bin/env bash
set -euo pipefail

git config --local merge.ours-always.driver true
echo "merge drivers configured"
