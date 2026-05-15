#!/usr/bin/env bash
# Processor-brick convention checks for Queenswood.
# Sources the invariants in docs/tdd/processor-bricks.md.
# Prints PASS/FAIL per check; FAIL includes file:line refs.
#
# A "processor brick" is any components/bank-X with both
# commands.clj and store.clj.
#
#   bash checks.sh                       # branch scope (default)
#   bash checks.sh --staged              # bricks with staged changes
#   bash checks.sh --all                 # every processor brick
#   bash checks.sh bank-cash-account ... # explicit brick names

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
  [ -f "components/$b/src/com/repldriven/mono/$bu/commands.clj" ] \
    && [ -f "components/$b/src/com/repldriven/mono/$bu/store.clj" ]
}

all_processors() {
  for d in components/bank-*; do
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
        components/bank-*/*)
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
WATCHER_FILES=()
NON_STORE_OR_WATCHER=()
REJECTION_FORBIDDEN=()  # store / interface / watcher / system
REJECTION_ADVISORY=()   # core

for b in "${PROCESSORS[@]}"; do
  bu=$(printf '%s' "$b" | tr - _)
  src="components/$b/src/com/repldriven/mono/$bu"
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
      */store.clj|*/watcher.clj) ;;
      *) NON_STORE_OR_WATCHER+=("$f") ;;
    esac
    case "$f" in
      */store.clj|*/interface.clj|*/watcher.clj|*/system.clj)
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

# 1. fdb required outside store.clj / watcher.clj.
section 'fdb required outside store.clj and watcher.clj'
out=""
if [ ${#NON_STORE_OR_WATCHER[@]} -gt 0 ]; then
  out=$(grep -nE 'com\.repldriven\.mono\.fdb\.interface' \
          "${NON_STORE_OR_WATCHER[@]}" 2>/dev/null)
fi
report 'fdb-leak' "$out"

# 2. watcher.clj using fdb beyond fdb/ctx->txn.
section 'watcher.clj fdb scope (only fdb/ctx->txn allowed)'
out=""
if [ ${#WATCHER_FILES[@]} -gt 0 ]; then
  out=$(grep -onE '\bfdb/[a-zA-Z][a-zA-Z0-9_!?*<>=+/.-]*' \
          "${WATCHER_FILES[@]}" 2>/dev/null \
          | grep -vE ':fdb/ctx->txn$')
fi
report 'watcher-fdb-scope' "$out"

# 3. domain.clj requiring fdb / bank-schema / its brick's own store.
section 'domain.clj requiring fdb / bank-schema / store'
out=""
if [ ${#DOMAIN_FILES[@]} -gt 0 ]; then
  out=$(grep -nE \
          'com\.repldriven\.mono\.(fdb\.interface|bank-schema\.interface|bank-[a-z0-9-]+\.store)' \
          "${DOMAIN_FILES[@]}" 2>/dev/null)
fi
report 'domain-impurity' "$out"

# 4. error/reject in store / interface / watcher / system — hard fail.
# commands.clj, domain.clj, validation.clj are sanctioned and not scanned.
section 'error/reject in store / interface / watcher / system'
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
