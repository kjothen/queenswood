#!/usr/bin/env bash
# Keeps Google Cloud account identifiers out of a public repository.
#
# None of these is a credential, and none of them grants anything on its
# own. They are worth withholding anyway: an organisation, folder or
# billing account id is what somebody pretexting a support call needs to
# sound like they already have access, and a document explaining how
# things are named needs names rather than account numbers.
#
# Resource *name shapes* are fine and wanted -- prj-qw01-c-mgmt-xxxxxx
# says something about how things are named. The random suffix on a
# realised one says nothing and identifies a project, so it is masked
# like the rest.
#
# Write a placeholder instead: folders/<folder-id>, organizations/<org-id>,
# and xxxxxx for a project id's suffix. Where a real one genuinely
# belongs, put `cloud-id-ok` in a comment on the same line.
#
# A commit message is checked too, and carries one rule of its own --
# see the bottom of this file.
#
#   bash check-cloud-ids.sh                 # every tracked file
#   bash check-cloud-ids.sh --staged        # staged changes (pre-commit)
#   bash check-cloud-ids.sh --message FILE  # a commit message (commit-msg)

set -euo pipefail

scope="${1:-}"
message=""

if [ "$scope" = "--message" ]; then
  message="${2:?--message needs the path to a commit message}"
  files="$message"
elif [ "$scope" = "--staged" ]; then
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

# A project id's random suffix: six hex characters closing a name. The
# checks above cannot see one -- it is neither a digit run nor prefixed
# by projects/ -- which is how a live management project id sat in this
# file's own header, and an older one in a chart's values.
#
# The leading [0-9a-z] is what makes this safe repo-wide. A suffix always
# follows a name character, where a negative minor-unit amount follows
# whitespace or a delimiter, so `-100000` in a balance fixture cannot
# match and no tree has to be excluded. Naming schemes are not assumed
# either: this predates prj- and would have caught it.
#
# The mask cannot match, x not being a hex digit.
hits=$(printf '%s\n' "$files" | xargs grep -nHE \
  '[0-9a-z]-[0-9a-f]{6}([^0-9a-z-]|$)' 2>/dev/null \
  | grep -v 'cloud-id-ok' || true)
[ -n "$hits" ] && report "project id suffix" "$hits"

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

# A realised resource, named in prose that is permanent. Not an account
# identifier and not sensitive -- a Job's name carries a hash of its own
# pod spec, and a message quoting one reads as though it means something
# when it names one cluster's state on one afternoon. The guard's
# opening rule applies: name shapes are wanted, realised ones are not,
# so write <release>-bootstrap-<hash>.
#
# Message-only, and that is the whole reason it can be this loose: the
# same shape is every UUID in a test fixture, so repo-wide it would
# match hundreds of lines that are exactly what they should be.
if [ -n "$message" ]; then
  hits=$(grep -nHE '[0-9a-z]-[0-9a-f]{8,}([^0-9a-z-]|$)' "$message" 2>/dev/null \
    | grep -v 'cloud-id-ok' || true)
  [ -n "$hits" ] && report "realised resource id in a commit message" "$hits"
fi

exit $fail
