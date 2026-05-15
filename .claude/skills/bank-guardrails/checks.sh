#!/usr/bin/env bash
# Critical-guardrail checks for Queenswood Clojure code.
# Sources the rules listed under "Critical guardrails" in CLAUDE.md.
# Prints PASS/FAIL per check; FAIL includes file:line refs.
#
# Default scope: files changed on the current branch (commits ahead
# of main, plus working-tree modifications and untracked files).
#
#   bash checks.sh             # branch scope (default)
#   bash checks.sh --staged    # only staged changes
#   bash checks.sh --all       # every tracked Clojure file
#   bash checks.sh <path> ...  # explicit paths

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
    base=$(git merge-base HEAD main 2>/dev/null \
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
INTERFACE_FILES=( $(printf '%s\n' "${ALL_FILES[@]}" | grep '/interface\.clj$') )
TEST_FILES=(      $(printf '%s\n' "${ALL_FILES[@]}" | grep '_test\.clj$') )
SRC_CLJ=(         $(printf '%s\n' "${ALL_FILES[@]}" | grep -E '\.(clj|cljc)$') )
NON_UTILITY_CLJ=( $(printf '%s\n' "${SRC_CLJ[@]}"   | grep -v '^components/utility/') )

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

# 1. Throwing from interface.clj.
section 'Throwing from interface.clj'
out=""
if [ ${#INTERFACE_FILES[@]} -gt 0 ]; then
  out=$(grep -nE '\b(throw|ex-info|ex-cause)\b' "${INTERFACE_FILES[@]}" 2>/dev/null)
fi
report 'throw-from-interface' "$out"

# 2. Raw time / id helpers used directly (outside the utility brick).
section 'Raw time/id helpers (use the `utility` brick)'
out=""
if [ ${#NON_UTILITY_CLJ[@]} -gt 0 ]; then
  out=$(grep -nE '\((random-uuid|(java\.util\.)?UUID/randomUUID|(java\.time\.)?Instant/now|System/currentTimeMillis)\b' \
          "${NON_UTILITY_CLJ[@]}" 2>/dev/null)
fi
report 'raw-time-id' "$out"

# 3. use-fixtures in tests.
section 'use-fixtures in tests'
out=""
if [ ${#TEST_FILES[@]} -gt 0 ]; then
  out=$(grep -nE '\buse-fixtures\b' "${TEST_FILES[@]}" 2>/dev/null)
fi
report 'use-fixtures-in-tests' "$out"

# 4. Cross-unit internal imports.
# Rules:
#   - intra-unit (target == own) is always fine
#   - target is a component: only `.interface` and `.system` are public
#   - target is a base: only `.system`, and only when the importer is itself
#     a base (component → base is the wrong direction)
#   - target is neither a component nor a base: ignore (generated namespaces
#     like com.repldriven.mono.schemas.* live under a brick's gen/ tree
#     under a non-matching prefix)
section 'Cross-unit internal imports (components and bases)'
out=""
if [ ${#SRC_CLJ[@]} -gt 0 ]; then
  bricks_us=$(ls components 2>/dev/null | tr - _ | paste -sd, -)
  bases_us=$(ls bases 2>/dev/null | tr - _ | paste -sd, -)
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
      b = parts[2]; gsub("-", "_", b)
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
      while (match(line, /com\.repldriven\.mono\.[a-z0-9_]+\.[a-z0-9_]+/)) {
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
          if (!(own_kind == "base" && sub_ns == "system")) bad = 1
        }
        if (bad) print FILENAME ":" FNR ": " s
      }
    }
  ' "${SRC_CLJ[@]}" 2>/dev/null)
fi
report 'cross-unit-internal' "$out"

# 5. Comment-block bloat — runs of 5+ consecutive `;`-comment lines.
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
report 'comment-block-bloat' "$out"
