#!/usr/bin/env bash
# Assert the pinned versions that cannot read versions.json still agree with
# it. flake.nix and the CI workflows read versions.json directly; Dockerfile
# ARG defaults and Helm values cannot, so they are checked here instead.
#
# A FoundationDB client can only talk to a cluster on the same protocol
# version, so a drifted copy is not a cosmetic inconsistency -- it surfaces
# as a connection failure that says nothing about versions.
set -uo pipefail

cd "$(dirname "$0")/.."

versions=versions.json
fdb=$(jq -r '.foundationdb.version' "$versions")
fdb_minor=${fdb%.*}
fail=0

# Report one comparison. An empty `got` means the pattern matched nothing,
# which is a failure in its own right: it means the file moved on and this
# check silently stopped looking at anything.
check() {
  local what="$1" want="$2" got="$3"
  if [ -z "$got" ]; then
    printf '  %-52s \033[31mnot found\033[0m (expected %s)\n' "$what" "$want"
    fail=1
  elif [ "$got" = "$want" ]; then
    printf '  %-52s \033[32m%s\033[0m\n' "$what" "$got"
  else
    printf '  %-52s \033[31m%s\033[0m (expected %s)\n' "$what" "$got" "$want"
    fail=1
  fi
}

echo "Pinned copies of FoundationDB $fdb, against $versions:"

for f in infra/docker/service/Dockerfile infra/docker/fdb/Dockerfile; do
  check "$f (ARG FDB_VERSION)" "$fdb" \
    "$(sed -n 's/^ARG FDB_VERSION=\([0-9][0-9.]*\).*/\1/p' "$f" | head -1)"
done

values=infra/helm/queenswood/values.yaml

# Read with awk rather than a YAML parser so this needs only jq, which both
# the devshell and the CI runners already have. The two tools named `yq` --
# nixpkgs ships the Python jq-wrapper, the runners ship mikefarah's Go one --
# take incompatible expressions, and depending on either invites a check that
# passes locally and misbehaves in CI.
check "$values (fdb.version)" "$fdb" \
  "$(awk '/^fdb:/ {f = 1; next}
          f && /^[^ ]/ {f = 0}
          f && /^  version:/ {gsub(/["]/, "", $2); print $2; exit}' "$values")"

# The operator copies libfdb_c_<minor>.so out of the monitor image, so the
# enabled initContainers key must be the cluster's minor and its tag must be
# on that same minor. Exactly one key is left non-null; the rest are nulled to
# avoid pulling monitor images we never run. An entry is enabled when the key
# line carries no value -- `"7.4":` opens a block, `"7.1": null` does not.
check "$values (enabled initContainer)" "$fdb_minor" \
  "$(awk '/^  initContainers:/ {f = 1; next}
          f && /^[^ ]/ {f = 0}
          f && /^    "/ && $2 == "" {
            k = $1; gsub(/["]|:$/, "", k); out = out sep k; sep = ","
          }
          END {print out}' "$values")"

check "$values (initContainer $fdb_minor tag)" "$fdb" \
  "$(awk -v key="\"$fdb_minor\":" '
          $1 == key {f = 1; next}
          f && /^    "/ {f = 0}
          f && /^        tag:/ {print $2; exit}' "$values")"

if [ "$fail" -ne 0 ]; then
  cat <<EOF

Update the files above to match $versions, or change $versions if the
intent was to move the pin. The FoundationDB client, the client baked into
the service image, and the deployed cluster all share a protocol version and
must move together.
EOF
  exit 1
fi

echo "All pinned copies agree."
