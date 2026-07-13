#!/usr/bin/env bash
# Mechanically extracts the source-of-truth content for each section of a
# Tessl rule file, from the recipes/ADRs it links to in its trailing
# `See [...]` line. Prints raw extracted material per section for the
# agent to compress into prose — this script does no writing, no judgment,
# and no invention; it only locates and prints what's already there.
#
# Recipes: pulls the `## Rules` section's `**MUST:**` / `**MUST NOT:**`
# bullets (and `**SHOULD:**` if present) verbatim.
# ADRs: pulls the `## Decision` section's lead paragraph, plus a
# colon-ending intro line and its immediately-following list, if that
# shape is present (covers both "The rules: 1. ..." normative
# enumerations and "Worked examples: - ..." illustrative lists alike —
# a markdown-shape match can't tell those apart; the agent composing the
# rule body decides which of the extracted material is normative).
#
# A recipe with no `## Rules` section, or an ADR with no `## Decision`
# section, is an ERROR for that source — never silently skipped or
# guessed at.
#
#   bash extract.sh [rule-file]   # default: plugins/idioms/rules/idioms.md

set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

RULE_FILE="${1:-plugins/idioms/rules/idioms.md}"
RULE_DIR="$(dirname "$RULE_FILE")"

[ -f "$RULE_FILE" ] || { echo "No such rule file: $RULE_FILE" >&2; exit 1; }

# --- Split the rule file into one temp file per `##` section --------------
# (a shell `while read` loop can't carry a multi-line body through a pipe,
# so each section is written to its own file instead of streamed inline)

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

awk -v outdir="$WORKDIR" '
  /^## / {
    n++
    file = outdir "/" n ".section"
    print $0 > file
    next
  }
  n > 0 { print > (outdir "/" n ".section") }
' "$RULE_FILE"

# --- Extract one recipe'"'"'s Rules block -------------------------------------

extract_recipe() {
  local doc="$1"
  awk '
    /^## Rules[[:space:]]*$/ { in_rules = 1; next }
    in_rules && /^## / { in_rules = 0 }
    in_rules { print }
  ' "$doc"
}

# --- Extract one ADR'"'"'s Decision lead (+ colon-intro'"'"'d list if present) ---

extract_adr() {
  local doc="$1"
  local raw
  raw=$(awk '
    /^## Decision[[:space:]]*$/ { in_decision = 1; next }
    in_decision && /^#{2,3} / { exit }
    in_decision { print }
  ' "$doc")
  [ -z "$raw" ] && return
  printf '%s' "$raw" | awk -v RS='' '
    NR == 1 { print; next }
    NR == 2 {
      if ($0 ~ /^([0-9]+\.|[-*]) /) { print; exit }
      if ($0 ~ /:$/) { intro = $0; want_next = 1; next }
      exit
    }
    NR == 3 {
      if (want_next && $0 ~ /^([0-9]+\.|[-*]) /) { print intro "\n"; print }
      exit
    }
  '
}

# --- Pull the full (possibly multi-line) "See ..." block from a section ---

extract_see_block() {
  local file="$1"
  awk '
    /^See / { collecting = 1 }
    collecting { print; if ($0 ~ /\.$/) exit }
  ' "$file"
}

echo "Rule file: $RULE_FILE"
echo

for f in "$WORKDIR"/*.section; do
  [ -f "$f" ] || continue
  title=$(head -1 "$f")
  see_block=$(extract_see_block "$f")
  [ -z "$see_block" ] && continue

  printf '### %s\n' "$title"
  printf 'See-block: %s\n\n' "$(printf '%s' "$see_block" | tr '\n' ' ')"

  links=$(printf '%s\n' "$see_block" | grep -oE '\]\([^)]+\)' | tr -d '][()')

  while IFS= read -r rel; do
    [ -z "$rel" ] && continue
    doc_dir="$(cd "$RULE_DIR/$(dirname "$rel")" 2>/dev/null && pwd)" || {
      echo "ERROR: cannot resolve path $rel (from $title)"
      continue
    }
    doc="$doc_dir/$(basename "$rel")"
    doc="${doc#$REPO_ROOT/}"

    if [[ "$doc" == docs/adr/* ]]; then
      out=$(extract_adr "$doc")
      if [ -z "$out" ]; then
        echo "ERROR: $doc has no ## Decision section"
      else
        printf -- '--- %s (## Decision) ---\n%s\n\n' "$doc" "$out"
      fi
    elif [[ "$doc" == docs/recipes/* ]]; then
      out=$(extract_recipe "$doc")
      if [ -z "$out" ]; then
        echo "ERROR: $doc has no ## Rules section"
      else
        printf -- '--- %s (## Rules) ---\n%s\n\n' "$doc" "$out"
      fi
    else
      echo "SKIP: $doc (not under docs/adr/ or docs/recipes/)"
    fi
  done <<< "$links"
done
