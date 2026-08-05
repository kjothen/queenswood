#!/usr/bin/env bash
#
# linear-plan-loop.sh
#
# The other half of the pipeline: linear-agent-loop.sh IMPLEMENTS issues
# labelled "ai-ready"; this script PRODUCES that label. For each leaf
# sub-issue (an issue with a parent and no children of its own — a real
# work item, not an organizational umbrella like "Accounts Lifecycle Gaps")
# in a Linear project that doesn't already have an implementation-plan
# comment, it runs Claude Code headlessly (subscription auth, not API
# billing, read-only tools — no Edit, no Bash mutation of the repo) to
# research the codebase and write a plan comment in the same shape used
# throughout this project: "## Implementation plan — decisions & scope"
# with Decisions / Scope of this increment / Deferred follow-ups sections,
# every fork in the issue's own "Work" section resolved, real file:line
# citations. It then posts that comment, judges Opus-vs-Sonnet complexity
# (money-path logic, schema/index/invariant changes, or first-of-kind
# cross-brick wiring -> Opus; pattern-following implementation -> Sonnet),
# and labels the issue Complexity:<X> + ai-ready so linear-agent-loop.sh
# picks it up next.
#
# Safe to run with real parallelism (MAX_PARALLEL) unlike the sibling
# executor script: this one makes no git/worktree/branch changes at all —
# every candidate is read against the SAME shared REPO_DIR checkout with no
# mutation, so there's nothing for concurrent runs to clash over.
#
# SCOPING MATTERS: a Linear project can contain many independent umbrella
# threads (this workspace's "Known Limitations" project has ten — QNS-1
# through QNS-10 — each with its own tree of sub-issues). Leaving
# ROOT_ISSUE unset scopes to the WHOLE project, which may pull in issues
# from initiatives you haven't reviewed. Set ROOT_ISSUE to scope to one
# ancestor's subtree instead (checked up to 4 levels of nesting, which
# covers this workspace's project -> domain-umbrella -> leaf-issue depth).
#
# A candidate is "already planned" (and thus skipped) if it has a
# Complexity:* or ai-ready label, OR any comment whose body starts with
# "## Implementation plan" — so re-running this script is always safe;
# nothing gets planned twice.
#
# Usage:
#   ./linear-plan-loop.sh                 # orchestrate the full run
#   ./linear-plan-loop.sh --process FILE  # internal: process one issue (manual retry)
#
# Requires: curl, jq, claude (all on PATH), LINEAR_API_KEY set.

set -euo pipefail

# ---------------------------------------------------------------------------
# Config (override via environment)
# ---------------------------------------------------------------------------
LINEAR_API_KEY="${LINEAR_API_KEY:?Set LINEAR_API_KEY}"
LINEAR_PROJECT="${LINEAR_PROJECT:-Known Limitations}"
ROOT_ISSUE="${ROOT_ISSUE:-}"                      # e.g. "QNS-1" — empty = whole project
LINEAR_LABEL_READY="${LINEAR_LABEL_READY:-ai-ready}"
COMPLEXITY_LABEL_PREFIX="${COMPLEXITY_LABEL_PREFIX:-Complexity:}"

REPO_DIR="${REPO_DIR:-$(pwd)}"
QUEUE_DIR="${QUEUE_DIR:-${REPO_DIR}/.agent-loop/plan-queue}"
LOG_DIR="${LOG_DIR:-${REPO_DIR}/.agent-loop/plan-logs}"
CONTEXT_DIR="${CONTEXT_DIR:-${REPO_DIR}/.agent-loop/plan-context}"
ALL_NODES_FILE="${CONTEXT_DIR}/all-nodes.json"

MAX_PARALLEL="${MAX_PARALLEL:-3}"
CLAUDE_ALLOWED_TOOLS="${CLAUDE_ALLOWED_TOOLS:-Read,Grep,Glob,Bash}"
DEFAULT_MODEL="${DEFAULT_MODEL:-claude-fable-5}"
DRY_RUN="${DRY_RUN:-false}"

LINEAR_ENDPOINT="https://api.linear.app/graphql"

mkdir -p "$QUEUE_DIR" "$LOG_DIR" "$CONTEXT_DIR"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log() {
  echo "[$(date -u +%H:%M:%S)] $*" >&2
}

linear_graphql() {
  local query="$1"
  local variables="${2:-{\}}"
  curl -s "$LINEAR_ENDPOINT" \
    -H "Authorization: $LINEAR_API_KEY" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg q "$query" --argjson v "$variables" '{query: $q, variables: $v}')"
}

get_label_id() {
  local name="$1"
  local query='
    query($name: String!) {
      issueLabels(filter: { name: { eq: $name } }) {
        nodes { id }
      }
    }'
  linear_graphql "$query" "$(jq -n --arg name "$name" '{name: $name}')" \
    | jq -r '.data.issueLabels.nodes[0].id // empty'
}

get_current_label_ids() {
  local issue_id="$1"
  local query='
    query($id: String!) {
      issue(id: $id) {
        labels { nodes { id } }
      }
    }'
  linear_graphql "$query" "$(jq -n --arg id "$issue_id" '{id: $id}')" \
    | jq -c '[.data.issue.labels.nodes[].id]'
}

post_comment() {
  local issue_id="$1"
  local body="$2"

  local mutation='
    mutation($issueId: String!, $body: String!) {
      commentCreate(input: { issueId: $issueId, body: $body }) { success }
    }'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would comment on ${issue_id} (${#body} chars)"
    return 0
  fi

  linear_graphql "$mutation" "$(jq -n --arg id "$issue_id" --arg b "$body" '{issueId: $id, body: $b}')" \
    > /dev/null
}

add_labels() {
  # Adds label names to an issue, preserving whatever labels it already has
  # (unlike a "set" that replaces the whole array). $1 = queue file,
  # remaining args = label names to add.
  local file="$1"
  shift
  local issue_id current_label_ids new_label_ids name id
  issue_id=$(jq -r '.id' "$file")
  current_label_ids=$(get_current_label_ids "$issue_id")

  local add_ids="[]"
  for name in "$@"; do
    id=$(get_label_id "$name")
    if [[ -z "$id" ]]; then
      log "WARNING: label '${name}' does not exist in Linear, skipping it"
      continue
    fi
    add_ids=$(jq -c --arg id "$id" '. + [$id]' <<< "$add_ids")
  done

  new_label_ids=$(jq -n --argjson current "$current_label_ids" --argjson add "$add_ids" \
    '($current + $add) | unique')

  local mutation='
    mutation($id: String!, $labelIds: [String!]!) {
      issueUpdate(id: $id, input: { labelIds: $labelIds }) { success }
    }'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would set labels on ${issue_id} -> ${new_label_ids}"
    return 0
  fi

  linear_graphql "$mutation" "$(jq -n --arg id "$issue_id" --argjson labelIds "$new_label_ids" '{id: $id, labelIds: $labelIds}')" \
    > /dev/null
}

# ---------------------------------------------------------------------------
# Step 1: fetch leaf sub-issues in the project that don't have a plan yet
# ---------------------------------------------------------------------------

fetch_candidates() {
  log "Fetching leaf sub-issues of project '${LINEAR_PROJECT}' from Linear${ROOT_ISSUE:+ (scoped to the ${ROOT_ISSUE} subtree)}"

  rm -f "${QUEUE_DIR}"/*.json

  # parent: { null: false }        -> is a sub-issue (has a parent)
  # children: { length: { eq: 0 } } -> is a LEAF (not itself an umbrella
  #                                     with further children, e.g. a
  #                                     domain-grouping issue)
  # Ancestor chain fetched 4 levels deep to support ROOT_ISSUE scoping —
  # covers this workspace's project -> domain-umbrella -> leaf-issue depth;
  # widen if a deeper hierarchy shows up elsewhere.
  local query='
    query($project: String!) {
      issues(filter: {
        project: { name: { eq: $project } },
        parent: { null: false },
        children: { length: { eq: 0 } },
        state: { type: { neq: "completed" } }
      }, first: 250) {
        nodes {
          id
          identifier
          title
          description
          url
          labels { nodes { id name } }
          comments { nodes { body } }
          parent {
            identifier
            parent {
              identifier
              parent {
                identifier
                parent { identifier }
              }
            }
          }
        }
      }
    }'

  local response
  response=$(linear_graphql "$query" "$(jq -n --arg project "$LINEAR_PROJECT" '{project: $project}')")

  if [[ "$(echo "$response" | jq -r '.errors // empty')" != "" ]]; then
    log "Linear API returned errors:"
    echo "$response" | jq '.errors' >&2
    exit 1
  fi

  # Full leaf-issue set, saved for sibling-context lookups when building
  # each candidate's prompt (see siblings_for below).
  echo "$response" | jq -c '.data.issues.nodes' > "$ALL_NODES_FILE"

  local candidates
  candidates=$(echo "$response" | jq -c \
    --arg prefix "$COMPLEXITY_LABEL_PREFIX" \
    --arg ready "$LINEAR_LABEL_READY" \
    --arg root "$ROOT_ISSUE" \
    '
    .data.issues.nodes[]
    | select(
        ([.labels.nodes[].name] | any(startswith($prefix))) == false
        and ([.labels.nodes[].name] | any(. == $ready)) == false
        and ([.comments.nodes[].body] | any(startswith("## Implementation plan"))) == false
        and (
          $root == ""
          or .parent.identifier == $root
          or .parent.parent.identifier == $root
          or .parent.parent.parent.identifier == $root
          or .parent.parent.parent.parent.identifier == $root
        )
      )
    ')

  local count
  count=$(jq -s 'length' <<< "$candidates")
  log "Found ${count} issue(s) needing a plan"

  echo "$candidates" | while read -r issue; do
    local identifier
    identifier=$(echo "$issue" | jq -r '.identifier')
    echo "$issue" > "${QUEUE_DIR}/${identifier}.json"
  done
}

siblings_for() {
  # Other leaf issues sharing this candidate's immediate parent (e.g. other
  # Accounts-domain issues), as {identifier, title, labels} — including
  # ones already planned, since those are exactly the ones that may have
  # already claimed a shared resource (a proto enum tag, an FDB index name)
  # worth coordinating with.
  local file="$1"
  local identifier parent_identifier
  identifier=$(jq -r '.identifier' "$file")
  parent_identifier=$(jq -r '.parent.identifier' "$file")

  jq -c --arg pid "$parent_identifier" --arg self "$identifier" '
    [.[] | select(.parent.identifier == $pid and .identifier != $self)
     | {identifier, title, labels: [.labels.nodes[].name]}]
  ' "$ALL_NODES_FILE"
}

# ---------------------------------------------------------------------------
# Step 2: process a single issue — research, write a plan, post + label it
# ---------------------------------------------------------------------------

process_issue() {
  local file="$1"
  local identifier issue_id title description url log_file siblings

  identifier=$(jq -r '.identifier' "$file")
  issue_id=$(jq -r '.id' "$file")
  title=$(jq -r '.title' "$file")
  description=$(jq -r '.description // ""' "$file")
  url=$(jq -r '.url' "$file")
  log_file="${LOG_DIR}/${identifier}.json"
  siblings=$(siblings_for "$file")

  local siblings_section=""
  if [[ "$(jq 'length' <<< "$siblings")" -gt 0 ]]; then
    siblings_section=$(jq -r '
      "Other issues under the same parent (for coordination awareness, e.g. a shared proto enum tag, FDB index name, or capability another one already claimed; check its own Linear comments if it looks relevant, do not assume):\n"
      + (map("- " + .identifier + " — " + .title + (if (.labels | length) > 0 then " [" + (.labels | join(", ")) + "]" else "" end)) | join("\n"))
    ' <<< "$siblings")
  fi

  log "[${identifier}] Researching and planning (model: ${DEFAULT_MODEL})"

  local prompt_file
  prompt_file=$(mktemp)
  cat > "$prompt_file" <<EOF
Linear issue ${identifier}: ${title}
${url}

${description}

${siblings_section}

Research this issue thoroughly against the actual repo state — read the
files and docs it cites, verify every claim, don't guess. Then write an
implementation plan as a single Linear comment, in exactly this shape
(matching the convention already used throughout this project):

## Implementation plan — decisions & scope

<one-line orientation sentence>

**Decisions**
- **<fork the issue's Work section left open> → <your resolution>.** <why,
  citing real file:line references you've actually opened and verified>
...

**Scope of this increment**
- <exact files/namespaces touched, new fns, tests — bounded to ONE landable
  increment, not the whole issue if it's large>

**Deferred follow-ups** (out of scope here)
- <anything you're deliberately not doing now, and why>

Requirements for the plan:
- Resolve EVERY fork the issue's "Work" section leaves open. Whoever
  implements this plan may be a weaker/cheaper model than you — they must
  be able to execute without making further design decisions.
- Cite real file paths and line numbers you have actually verified by
  reading the file, not recalled or guessed.
- Follow this repo's own conventions (CLAUDE.md / AGENTS.md are already
  loaded as project context) — in particular: proto2 fields are always
  optional and enum values are appended, never renumbered; a new FDB record
  type or Avro schema must be registered in BOTH its production and test
  registry; whether a write earns command status or stays a synchronous
  interface write follows ADR-0018's four-property test. If an existing
  recipe or checklist in docs/recipes/ already covers the mechanical steps
  for this kind of change, reference it by name instead of restating it.
- 300-600 words. Keep it tight — this is a decisions memo, not a design doc.

Then judge implementation complexity for whoever executes this plan next:
- "opus" — the plan involves money-path logic (payments, balances,
  interest), a schema/index/invariant change with real correctness risk,
  or first-of-kind cross-brick wiring (e.g. the first watcher a brick has
  ever had).
- "sonnet" — pattern-following implementation: mirrors an existing
  precedent closely, decisions are fully pre-resolved above, low blast
  radius if slightly wrong.

Do not modify any files. Do not run builds or tests. Research only.
EOF

  local schema='{"type":"object","properties":{"plan_markdown":{"type":"string"},"complexity":{"type":"string","enum":["opus","sonnet"]}},"required":["plan_markdown","complexity"]}'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would run claude -p (model=${DEFAULT_MODEL}) from ${REPO_DIR} with prompt from ${prompt_file}"
    rm -f "$prompt_file"
    return 0
  fi

  (
    cd "$REPO_DIR"
    cat "$prompt_file" | claude -p \
      --model "$DEFAULT_MODEL" \
      --allowedTools "$CLAUDE_ALLOWED_TOOLS" \
      --permission-mode acceptEdits \
      --output-format json \
      --json-schema "$schema" \
      > "$log_file"
  )
  rm -f "$prompt_file"

  if [[ "$(jq -r '.is_error // false' "$log_file")" == "true" ]]; then
    log "[${identifier}] Claude run reported an error — leaving unplanned, will be retried on the next run"
    return 1
  fi

  local plan_markdown complexity
  plan_markdown=$(jq -r '.structured_output.plan_markdown // empty' "$log_file")
  complexity=$(jq -r '.structured_output.complexity // empty' "$log_file" | tr '[:upper:]' '[:lower:]')

  if [[ -z "$plan_markdown" || -z "$complexity" ]]; then
    log "[${identifier}] Claude finished but didn't report a plan/complexity — leaving unplanned, will be retried on the next run"
    return 1
  fi

  local complexity_label
  complexity_label="${COMPLEXITY_LABEL_PREFIX}$(tr '[:lower:]' '[:upper:]' <<< "${complexity:0:1}")${complexity:1}"

  post_comment "$issue_id" "$plan_markdown"
  add_labels "$file" "$complexity_label" "$LINEAR_LABEL_READY"

  log "[${identifier}] Done -> ${complexity_label}, ${LINEAR_LABEL_READY}"
}

# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if [[ "${1:-}" == "--process" ]]; then
  process_issue "$2"
  exit $?
fi

fetch_candidates

queued=("${QUEUE_DIR}"/*.json)
if [[ ! -e "${queued[0]:-}" ]]; then
  log "No issues to plan"
  exit 0
fi

log "Planning ${#queued[@]} issue(s) with max parallelism ${MAX_PARALLEL}"

printf '%s\n' "${queued[@]}" | xargs -P "$MAX_PARALLEL" -I{} "$0" --process {}

log "Run complete. Logs in ${LOG_DIR}"
