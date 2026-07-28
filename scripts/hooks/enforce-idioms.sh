#!/usr/bin/env bash
# Deterministic guardrail linter for Queenswood Clojure code.
# Enforces the "Critical guardrails" from CLAUDE.md that a deterministic
# grep can decide; the `no-raw-throw` throw check is owned by semgrep
# (.semgrep.yml). Run by the pre-commit hook in --staged mode; also
# runnable standalone for a whole-branch or explicit-path sweep.
#
# Exit status: non-zero if any BLOCKING check fails, so it can gate a
# commit. `comment-block-bloat` is advisory (WARN, never blocks) — long
# comment blocks are sometimes legitimately load-bearing.
#
#   bash enforce-idioms.sh           # branch scope (default)
#   bash enforce-idioms.sh --staged  # only staged changes (pre-commit)
#   bash enforce-idioms.sh --all     # every tracked Clojure file
#   bash enforce-idioms.sh <path>... # explicit paths

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

# --- Collect candidate files --------------------------------------------

case "$scope" in
  branch)
    base=$(git merge-base HEAD origin/main 2>/dev/null \
             || git merge-base HEAD main 2>/dev/null \
             || git rev-parse origin/main 2>/dev/null \
             || git rev-parse main 2>/dev/null)
    RAW=( $({
      [ -n "$base" ] && git diff --name-only "$base"..HEAD 2>/dev/null
      git diff --name-only 2>/dev/null
      git ls-files --others --exclude-standard 2>/dev/null
    } | sort -u) )
    ;;
  staged)
    RAW=( $(git diff --name-only --cached 2>/dev/null | sort -u) )
    ;;
  all)
    RAW=( $(git ls-files 2>/dev/null | sort -u) )
    ;;
  explicit)
    RAW=( "${EXPLICIT[@]}" )
    ;;
esac

# Filter to Clojure / deps.edn and keep only files that still exist.
ALL_FILES=()
for f in "${RAW[@]}"; do
  # Skip eval fixtures — planted violations, test inputs, not code.
  case "$f" in */evals/*|evals/*) continue ;; esac
  case "$f" in
    *.clj|*.cljc|*deps.edn) [ -f "$f" ] && ALL_FILES+=("$f") ;;
  esac
done

if [ ${#ALL_FILES[@]} -eq 0 ]; then
  printf 'No Clojure files in scope (%s).\n' "$scope"
  exit 0
fi

printf 'Scope: %s — %d file(s)\n' "$scope" "${#ALL_FILES[@]}"

# Category subsets.
TEST_FILES=(      $(printf '%s\n' "${ALL_FILES[@]}" | grep '_test\.clj$') )
SRC_CLJ=(         $(printf '%s\n' "${ALL_FILES[@]}" | grep -E '\.(clj|cljc)$') )
NON_UTILITY_CLJ=( $(printf '%s\n' "${SRC_CLJ[@]}"   | grep -v '^components/utility/') )

# --- Helpers -------------------------------------------------------------

FAILED=0

section() { printf '\n### %s\n\n' "$1"; }

report() {
  local label="$1"
  local out="$2"
  local advisory="$3"   # non-empty → report as WARN, never gate the commit
  if [ -z "$out" ]; then
    printf 'PASS — %s\n' "$label"
  elif [ -n "$advisory" ]; then
    printf 'WARN — %s (advisory)\n\n```\n%s\n```\n' "$label" "$out"
  else
    printf 'FAIL — %s\n\n```\n%s\n```\n' "$label" "$out"
    FAILED=1
  fi
}

# --- Checks --------------------------------------------------------------

# 1. Raw time / id helpers used directly (outside the utility brick).
# Advisory until this earns a clean baseline the way `no-raw-throw` did:
# the repo still carries pre-existing hits, some legitimate (poll-timer
# elapsed-time math in test quiescence helpers, where `util/now`'s domain
# Instant doesn't fit) with no opt-out marker to exempt them. Promote to
# blocking once the domain-timestamp debt migrates to `util/now` and a
# `nosemgrep`-style opt-out covers the legitimate millis uses.
section 'Raw time/id helpers (use the `utility` brick)'
out=""
if [ ${#NON_UTILITY_CLJ[@]} -gt 0 ]; then
  out=$(grep -nE '\((random-uuid|(java\.util\.)?UUID/randomUUID|(java\.time\.)?Instant/now|System/currentTimeMillis)\b' \
          "${NON_UTILITY_CLJ[@]}" 2>/dev/null)
fi
report 'raw-time-id' "$out" advisory

# 2. use-fixtures in tests.
section 'use-fixtures in tests'
out=""
if [ ${#TEST_FILES[@]} -gt 0 ]; then
  out=$(grep -nE '\buse-fixtures\b' "${TEST_FILES[@]}" 2>/dev/null)
fi
report 'use-fixtures-in-tests' "$out"

# 3. Cross-unit internal imports.
# Rules:
#   - intra-unit (target == own) is always fine
#   - target is a component: only `.interface` and `.system` are public
#   - target is a base: only `.system` or `.api` are public (`.api` is the
#     multi-base aggregator pattern — bank-monolith wires several bases'
#     Reitit handlers into one process), and only when the importer is
#     itself a base (component → base is the wrong direction)
#   - target is neither a component nor a base: ignore (generated namespaces
#     like com.repldriven.mono.schemas.* live under a brick's gen/ tree
#     under a non-matching prefix)
section 'Cross-unit internal imports (components and bases)'
out=""
if [ ${#SRC_CLJ[@]} -gt 0 ]; then
  bricks_us=$(ls components 2>/dev/null | paste -sd, -)
  bases_us=$(ls bases 2>/dev/null | paste -sd, -)
  out=$(awk -v bricks="$bricks_us" -v bases="$bases_us" '
    BEGIN {
      n = split(bricks, a, ",")
      for (i = 1; i <= n; i++) if (a[i] != "") is_brick[a[i]] = 1
      n = split(bases, a, ",")
      for (i = 1; i <= n; i++) if (a[i] != "") is_base[a[i]] = 1
    }
    function unit_of(p, kind,    parts, n, b) {
      n = split(p, parts, "/")
      if (n < 4 || parts[3] != "src") return ""
      if (parts[1] != "components" && parts[1] != "bases") return ""
      b = parts[2]
      kind["k"] = (parts[1] == "components") ? "component" : "base"
      return b
    }
    FNR == 1 {
      delete kindbuf
      own = unit_of(FILENAME, kindbuf)
      own_kind = kindbuf["k"]
    }
    own == "" { next }
    {
      line = $0
      while (match(line, /com\.repldriven\.mono\.[a-z0-9_-]+\.[a-z0-9_-]+/)) {
        s = substr(line, RSTART, RLENGTH)
        line = substr(line, RSTART + RLENGTH)
        split(s, p, ".")
        target = p[4]
        sub_ns = p[5]
        if (target == own) continue
        bad = 0
        if (target in is_brick) {
          if (sub_ns != "interface" && sub_ns != "system") bad = 1
        } else if (target in is_base) {
          if (!(own_kind == "base" && (sub_ns == "system" || sub_ns == "api"))) bad = 1
        }
        if (bad) print FILENAME ":" FNR ": " s
      }
    }
  ' "${SRC_CLJ[@]}" 2>/dev/null)
fi
report 'cross-unit-internal' "$out"

# 4. interface.clj requires only its own component's local namespaces.
# Stricter than cross-unit-internal above: that check allows any file to
# require a foreign brick's `.interface`, but `interface.clj` itself must
# delegate to its own core/domain/store/etc. and never reach into another
# brick at all — not even via that brick's `.interface`, and not even a
# library-wrapper brick like `error`/`utility`. Composition across bricks
# belongs one level down, in core.clj. Advisory: known pre-existing debt
# (bank-cash-account, bank-party, bank-balance-query, bank-test-scenarios,
# bank-test-api-scenarios, secret) hasn't migrated yet — promote to
# blocking once it has.
section "interface.clj requires only its own component's namespaces"
out=""
if [ ${#SRC_CLJ[@]} -gt 0 ]; then
  bricks_us=$(ls components 2>/dev/null | paste -sd, -)
  bases_us=$(ls bases 2>/dev/null | paste -sd, -)
  out=$(awk -v bricks="$bricks_us" -v bases="$bases_us" '
    BEGIN {
      n = split(bricks, a, ",")
      for (i = 1; i <= n; i++) if (a[i] != "") is_brick[a[i]] = 1
      n = split(bases, a, ",")
      for (i = 1; i <= n; i++) if (a[i] != "") is_base[a[i]] = 1
    }
    function unit_of(p,    parts, n, b) {
      n = split(p, parts, "/")
      if (n < 4 || parts[3] != "src") return ""
      if (parts[1] != "components" && parts[1] != "bases") return ""
      b = parts[2]
      return b
    }
    FNR == 1 {
      own = unit_of(FILENAME)
      is_iface = (FILENAME ~ /\/interface\.clj$/)
    }
    own == "" || !is_iface { next }
    {
      line = $0
      while (match(line, /com\.repldriven\.mono\.[a-z0-9_-]+\.[a-z0-9_-]+/)) {
        s = substr(line, RSTART, RLENGTH)
        line = substr(line, RSTART + RLENGTH)
        split(s, p, ".")
        target = p[4]
        if (target == own) continue
        if ((target in is_brick) || (target in is_base)) {
          print FILENAME ":" FNR ": " s
        }
      }
    }
  ' "${SRC_CLJ[@]}" 2>/dev/null)
fi
report 'interface-imports-foreign-brick' "$out" advisory

# 5. Comment-block bloat — runs of 5+ consecutive `;`-comment lines.
# Advisory: a long block can be a legitimate load-bearing why-block, so
# this WARNs rather than blocking the commit.
section 'Comment-block bloat (>= 5 consecutive `;` lines)'
out=""
if [ ${#SRC_CLJ[@]} -gt 0 ]; then
  out=$(awk '
    function flush() {
      if (run >= 5) {
        print prevfile ":" start ": " run " consecutive comment lines"
      }
      run = 0
    }
    FNR == 1 { flush() }
    { prevfile = FILENAME }
    /^[[:space:]]*;/ {
      if (run == 0) start = FNR
      run++
      next
    }
    { flush() }
    END { flush() }
  ' "${SRC_CLJ[@]}" 2>/dev/null)
fi
report 'comment-block-bloat' "$out" advisory

# 6. api reads are query-only (ADR-0017).
# A CQRS/design invariant riding along here until the `design` plugin owns
# its enforcement. A domain brick with a `components/<brick>-query` sibling
# is split into a read side (`-query`) and a write side (the plain name).
# `api` request code may require the `-query` interface but must not
# require the write brick's interface — writes go over the bus as commands.
# The `system.clj` registration bundle is exempt: it bare-requires
# interfaces to register component-kinds (not to call them), and stays
# until the write brick leaves the API project (`poly check` Tier-2).
# The base path and namespace prefix are asserted below: this check is a
# pair of greps over names that a rename silently empties, and an empty
# candidate set reports PASS rather than failing.
section 'api reads are query-only'
out=""
API_BASE=bases/api/src
API_NS=com.repldriven.queenswood
if [ ! -d "$API_BASE" ]; then
  echo "  enforce-idioms: $API_BASE not found — check 6 cannot run" >&2
  FAILED=1
elif ! grep -rq "$API_NS\." "$API_BASE" 2>/dev/null; then
  echo "  enforce-idioms: no $API_NS.* under $API_BASE — check 6 cannot run" >&2
  FAILED=1
fi
API_SRC=( $(printf '%s\n' "${SRC_CLJ[@]}" \
             | grep -E "^$API_BASE/" | grep -v '/system\.clj$') )
if [ ${#API_SRC[@]} -gt 0 ]; then
  for qdir in components/*-query; do
    [ -d "$qdir" ] || continue
    write_brick=$(basename "${qdir%-query}")
    hits=$(grep -nE "$API_NS\.${write_brick}\.interface\b" \
             "${API_SRC[@]}" 2>/dev/null)
    [ -n "$hits" ] && out="${out}${hits}"$'\n'
  done
  out=$(printf '%s' "$out" | grep -v '^$' || true)
fi
report 'api-reads-are-query-only' "$out"

exit "$FAILED"
