#!/usr/bin/env bash
# Keeps Google Cloud account identifiers out of a public repository.
#
# None of these is a credential, and none of them grants anything on its
# own. They are worth withholding anyway: an organisation, folder or
# billing account id is what somebody pretexting a support call needs to
# sound like they already have access, and a document explaining how
# things are named needs names rather than account numbers.
#
# Resource *names* are fine and wanted -- prj-qw01-c-mgmt-e32b34 says
# something. Numeric account identifiers say nothing and cost something.
#
# Write a placeholder instead: folders/<folder-id>, organizations/<org-id>.
# Where a real one genuinely belongs, put `cloud-id-ok` in a comment on
# the same line.
#
#   bash check-cloud-ids.sh           # every tracked file
#   bash check-cloud-ids.sh --staged  # only staged changes (pre-commit)

set -euo pipefail

scope="${1:-}"

if [ "$scope" = "--staged" ]; then
  files=$(git diff --cached --name-only --diff-filter=ACM)
else
  files=$(git ls-files)
fi

# Binary and generated files carry digit runs that mean nothing here.
files=$(printf '%s\n' "$files" \
  | grep -Ev '\.(png|jpg|jpeg|gif|svg|pdf|ico|woff2?|lock)$' \
  | grep -Ev '(^|/)(package-lock\.json|yarn\.lock)$' \
  | while read -r f; do [ -f "$f" ] && echo "$f"; done || true)

[ -z "$files" ] && exit 0

fail=0

report() {
  fail=1
  echo "BLOCKING $1" >&2
  printf '%s\n' "$2" | sed 's/^/  /' >&2
  echo "  fix: write a placeholder, or add cloud-id-ok on the line" >&2
}

# A billing account id is three uppercase hex groups. Restricted to hex
# so that REPLACE-ME-GOOGLE-CLIENT-SECRET and 0X0X0X-0X0X0X-0X0X0X --
# both legitimate placeholders in this tree -- do not match.
hits=$(printf '%s\n' "$files" | xargs grep -nHE \
  '\b[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}\b' 2>/dev/null \
  | grep -v 'cloud-id-ok' || true)
[ -n "$hits" ] && report "billing account id" "$hits"

# A resource-manager id in the one shape that always means a real one.
hits=$(printf '%s\n' "$files" | xargs grep -nHE \
  '(organizations|folders|projects)/[0-9]{6,}' 2>/dev/null \
  | grep -v 'cloud-id-ok' || true)
[ -n "$hits" ] && report "organization, folder or project number" "$hits"

# Bare digit runs, in the trees that describe infrastructure. Not
# repo-wide: a minor-unit money amount in a UI fixture is the same shape
# and means something entirely different. A value after = is a setting,
# not an identifier -- retries=2147483647 is Integer.MAX_VALUE.
prose=$(printf '%s\n' "$files" \
  | grep -E '^(docs|justfiles|scripts|infra|\.github)/' || true)
if [ -n "$prose" ]; then
  hits=$(printf '%s\n' "$prose" | xargs grep -nHE \
    '(^|[^0-9A-Za-z_./=-])[0-9]{9,12}([^0-9A-Za-z_./-]|$)' 2>/dev/null \
    | grep -v 'cloud-id-ok' || true)
  [ -n "$hits" ] && report "bare account-length number" "$hits"
fi

exit $fail
