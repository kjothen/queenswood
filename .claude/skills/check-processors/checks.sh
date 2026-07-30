#!/usr/bin/env bash
# Processor-brick convention checks for Queenswood.
# Sources the invariants in docs/tdd/processor-bricks.md.
# Prints PASS/FAIL per check; FAIL includes file:line refs.
#
# A "processor brick" is any components/X with both commands.clj
# and store.clj.
#
#   bash checks.sh                  # branch scope (default)
#   bash checks.sh --staged         # bricks with staged changes
#   bash checks.sh --all            # every processor brick
#   bash checks.sh cash-account ... # explicit brick names

cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)" || exit 1

# --- Argument parsing ----------------------------------------------------

scope=branch
EXPLICIT=()
for arg in "$@"; do
  case "$arg" in
    --staged) scope=staged ;;
    --all)    scope=all ;;
    --branch) scope=branch ;;
    --*)      echo "Unknown flag: $arg" >&2; exit 2 ;;
    *)        EXPLICIT+=("$arg") ;;
  esac
done
if [ ${#EXPLICIT[@]} -gt 0 ]; then
  scope=explicit
fi

# --- Discover processor bricks ------------------------------------------

is_processor() {
  local b="$1"
  local bu
  bu=$(printf '%s' "$b" | tr - _)
  [ -f "components/$b/src/com/repldriven/queenswood/$bu/commands.clj" ] \
    && [ -f "components/$b/src/com/repldriven/queenswood/$bu/store.clj" ]
}

all_processors() {
  for d in components/*; do
    [ -d "$d" ] || continue
    local b
    b=$(basename "$d")
    if is_processor "$b"; then echo "$b"; fi
  done
}

case "$scope" in
  all)
    PROCESSORS=( $(all_processors) )
    ;;
  explicit)
    PROCESSORS=()
    for b in "${EXPLICIT[@]}"; do
      if is_processor "$b"; then
        PROCESSORS+=("$b")
      else
        echo "Warning: $b is not a processor brick (no commands.clj + store.clj); skipping." >&2
      fi
    done
    ;;
  branch|staged)
    case "$scope" in
      branch)
        base=$(git merge-base HEAD main 2>/dev/null \
                 || git rev-parse origin/main 2>/dev/null \
                 || git rev-parse main 2>/dev/null)
        CHANGED=( $({
          [ -n "$base" ] && git diff --name-only "$base"..HEAD 2>/dev/null
          git diff --name-only 2>/dev/null
          git ls-files --others --exclude-standard 2>/dev/null
        } | sort -u) )
        ;;
      staged)
        CHANGED=( $(git diff --name-only --cached 2>/dev/null | sort -u) )
        ;;
    esac
    TOUCHED=()
    for f in "${CHANGED[@]}"; do
      case "$f" in
        components/*/*)
          b=$(printf '%s' "$f" | awk -F/ '{print $2}')
          TOUCHED+=("$b")
          ;;
      esac
    done
    PROCESSORS=()
    if [ ${#TOUCHED[@]} -gt 0 ]; then
      for b in $(printf '%s\n' "${TOUCHED[@]}" | sort -u); do
        if is_processor "$b"; then PROCESSORS+=("$b"); fi
      done
    fi
    ;;
esac

if [ ${#PROCESSORS[@]} -eq 0 ]; then
  printf 'No processor bricks in scope (%s).\n' "$scope"
  exit 0
fi

printf 'Scope: %s — %d brick(s): %s\n' \
  "$scope" "${#PROCESSORS[@]}" "${PROCESSORS[*]}"

# --- Collect file lists per role ----------------------------------------

ALL_BRICK_FILES=()
DOMAIN_FILES=()
STORE_FILES=()
WATCHER_FILES=()        # must stay empty — the relay replaced watchers
NON_STORE=()
REJECTION_FORBIDDEN=()  # store / interface / events / system
REJECTION_ADVISORY=()   # core

for b in "${PROCESSORS[@]}"; do
  bu=$(printf '%s' "$b" | tr - _)
  src="components/$b/src/com/repldriven/queenswood/$bu"
  [ -d "$src" ] || continue
  for f in "$src"/*.clj; do
    [ -f "$f" ] || continue
    ALL_BRICK_FILES+=("$f")
    case "$f" in
      */domain.clj)  DOMAIN_FILES+=("$f") ;;
      */store.clj)   STORE_FILES+=("$f") ;;
      */watcher.clj) WATCHER_FILES+=("$f") ;;
    esac
    case "$f" in
      */store.clj) ;;
      *) NON_STORE+=("$f") ;;
    esac
    case "$f" in
      */store.clj|*/interface.clj|*/events.clj|*/system.clj)
        REJECTION_FORBIDDEN+=("$f") ;;
      */core.clj)
        REJECTION_ADVISORY+=("$f") ;;
      # commands.clj, domain.clj, validation.clj — sanctioned, not scanned
    esac
  done
done

# --- Helpers -------------------------------------------------------------

section() { printf '\n### %s\n\n' "$1"; }

report() {
  local label="$1"
  local out="$2"
  if [ -z "$out" ]; then
    printf 'PASS — %s\n' "$label"
  else
    printf 'FAIL — %s\n\n```\n%s\n```\n' "$label" "$out"
  fi
}

# --- Checks --------------------------------------------------------------

# 1. fdb required outside store.clj.
section 'fdb required outside store.clj'
out=""
if [ ${#NON_STORE[@]} -gt 0 ]; then
  out=$(grep -nE 'com\.repldriven\.queenswood\.fdb\.interface' \
          "${NON_STORE[@]}" 2>/dev/null)
fi
report 'fdb-leak' "$out"

# 2. watcher.clj at all. Reactive work reaches a brick as an event
# off the changelog relay, handled in events.clj — a watcher would run
# domain work inside the changelog checkpoint transaction again.
section 'watcher.clj in a processor brick'
out=""
if [ ${#WATCHER_FILES[@]} -gt 0 ]; then
  out=$(printf '%s\n' "${WATCHER_FILES[@]}")
fi
report 'watcher-present' "$out"

# 3. domain.clj requiring fdb / schema / its brick's own store.
section 'domain.clj requiring fdb / schema / store'
out=""
if [ ${#DOMAIN_FILES[@]} -gt 0 ]; then
  out=$(grep -nE \
          'com\.repldriven\.queenswood\.([a-z0-9-]+\.store|(fdb|schema)\.interface)' \
          "${DOMAIN_FILES[@]}" 2>/dev/null)
fi
report 'domain-impurity' "$out"

# 4. error/reject in store / interface / events / system — hard fail.
# commands.clj, domain.clj, validation.clj are sanctioned and not scanned.
section 'error/reject in store / interface / events / system'
out=""
if [ ${#REJECTION_FORBIDDEN[@]} -gt 0 ]; then
  out=$(grep -nE '\berror/reject\b' \
          "${REJECTION_FORBIDDEN[@]}" 2>/dev/null)
fi
report 'rejection-misplaced' "$out"

# 4b. error/reject in core.clj — advisory watch list.
section 'error/reject in core.clj (advisory)'
out=""
if [ ${#REJECTION_ADVISORY[@]} -gt 0 ]; then
  out=$(grep -nE '\berror/reject\b' \
          "${REJECTION_ADVISORY[@]}" 2>/dev/null)
fi
report 'rejection-in-core' "$out"

# 5. txn appearing anywhere in domain.clj.
# Strip string literals first so prefixes like (generate-id "txn")
# don't false-positive.
section 'txn references in domain.clj'
out=""
if [ ${#DOMAIN_FILES[@]} -gt 0 ]; then
  out=$(awk '
    {
      stripped = $0
      gsub(/"[^"]*"/, "", stripped)
      if (match(stripped, /[^a-zA-Z0-9_]txn[^a-zA-Z0-9_]/) \
          || match(stripped, /^txn[^a-zA-Z0-9_]/) \
          || match(stripped, /[^a-zA-Z0-9_]txn$/)) {
        print FILENAME ":" FNR ": " $0
      }
    }
  ' "${DOMAIN_FILES[@]}" 2>/dev/null)
fi
report 'domain-takes-txn' "$out"

# 6. schema/<Type>->... type-conversion calls in domain.clj.
section 'schema/ type-conversion calls in domain.clj'
out=""
if [ ${#DOMAIN_FILES[@]} -gt 0 ]; then
  out=$(grep -nE '\bschema/[A-Za-z][A-Za-z0-9_]*->[A-Za-z][A-Za-z0-9_]*' \
          "${DOMAIN_FILES[@]}" 2>/dev/null)
fi
report 'domain-schema-leak' "$out"
