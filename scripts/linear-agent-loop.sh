#!/usr/bin/env bash
#
# linear-agent-loop.sh
#
# Pulls Linear issues labelled "ai-ready", extracts the implementation plan
# from the latest comment, and runs Claude Code headlessly (subscription
# auth, not API billing) against each one — ONE ISSUE AT A TIME. After a PR
# opens, the run BLOCKS until you review and merge it (polling both the PR's
# own state and Linear's issue state, which the GitHub integration flips to
# "completed" on merge) before starting the next issue. This is deliberate,
# not a missing feature: several of these plans coordinate on shared,
# order-sensitive resources across issues (e.g. two proto-enum-tag claims
# racing on origin/main), so unmerged work must land — and be seen by
# whoever picks the next issue — before the next branch forks off main.
# If a PR is closed without merging, the whole run stops rather than
# ploughing ahead into the next issue.
#
# Each issue gets its own `git worktree` as a SIBLING of REPO_DIR (e.g.
# REPO_DIR=.../queenswood/mca -> worktree at .../queenswood/qns-11), created
# directly by this script off a freshly-fetched origin/main using Linear's
# recommended branch name. Deliberately NOT nested inside REPO_DIR: REPO_DIR
# is itself already a linked worktree in this setup, and a worktree inside a
# worktree's own working tree is a mistake (it'd show up as untracked
# clutter in REPO_DIR's own git status). Each new worktree gets
# `direnv allow .` run once right after creation — without it the nix
# devShell (clojure, zprint, clj-kondo, ...) never activates and
# `clj -X:deps prep` never runs there, so the worktree would be missing
# generated code and every headless run in it would fail deep inside with a
# confusing error. The commit/PR step is still delegated to the
# /commit-and-pr skill, run from inside that worktree via `direnv exec` so
# it inherits the same devShell environment. The worktree is reclaimed as
# soon as the PR is pushed; the branch itself (local ref + remote) is only
# deleted once the wait-for-merge poll confirms it actually merged — this
# repo does not have GitHub's "automatically delete head branches" enabled,
# so nothing else will clean it up. Model selection (Sonnet/Opus) is read
# from each issue's Complexity:* label.
#
# Usage:
#   ./linear-agent-loop.sh                 # orchestrate the full run
#   ./linear-agent-loop.sh --process FILE  # internal: process one issue (manual retry)
#
# Requires: curl, jq, claude, git, direnv, gh (all on PATH), LINEAR_API_KEY
# set, and the /commit-and-pr skill available to Claude Code in this repo.

set -euo pipefail

if ! command -v direnv >/dev/null 2>&1; then
  echo "direnv is required — each worktree needs 'direnv allow' to get the nix devShell (clojure, zprint, clj-kondo, ...) on PATH and to run 'clj -X:deps prep'. Install it or adjust this script if this repo doesn't use direnv." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh (GitHub CLI) is required — the run blocks between issues by polling each PR's merge state." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Config (override via environment)
# ---------------------------------------------------------------------------
LINEAR_API_KEY="${LINEAR_API_KEY:?Set LINEAR_API_KEY}"
LINEAR_LABEL_READY="${LINEAR_LABEL_READY:-ai-ready}"
LINEAR_LABEL_IN_PROGRESS="${LINEAR_LABEL_IN_PROGRESS:-ai-in-progress}"
LINEAR_LABEL_REVIEW="${LINEAR_LABEL_REVIEW:-ai-in-review}"
LINEAR_LABEL_FAILED="${LINEAR_LABEL_FAILED:-ai-failed}"

REPO_DIR="${REPO_DIR:-$(pwd)}"
QUEUE_DIR="${QUEUE_DIR:-${REPO_DIR}/.agent-loop/queue}"
LOG_DIR="${LOG_DIR:-${REPO_DIR}/.agent-loop/logs}"
# Sibling of REPO_DIR, not nested inside it — REPO_DIR is itself typically
# a linked worktree already (e.g. .../queenswood/mca), so new per-issue
# worktrees land alongside it (.../queenswood/qns-11), matching how the
# rest of this environment's worktrees are laid out.
WORKTREE_DIR="${WORKTREE_DIR:-$(dirname "$REPO_DIR")}"

CLAUDE_ALLOWED_TOOLS="${CLAUDE_ALLOWED_TOOLS:-Bash,Read,Edit}"
DEFAULT_MODEL="${DEFAULT_MODEL:-sonnet}"
COMPLEXITY_LABEL_PREFIX="${COMPLEXITY_LABEL_PREFIX:-Complexity:}"
DRY_RUN="${DRY_RUN:-false}"
# How often to poll for merge/close while blocked between issues.
WAIT_POLL_SECONDS="${WAIT_POLL_SECONDS:-60}"

LINEAR_ENDPOINT="https://api.linear.app/graphql"

mkdir -p "$QUEUE_DIR" "$LOG_DIR" "$WORKTREE_DIR"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log() {
  echo "[$(date -u +%H:%M:%S)] $*" >&2
}

linear_graphql() {
  # $1 = query/mutation string, $2 = variables JSON (optional, default {})
  local query="$1"
  local variables="${2:-{\}}"
  curl -s "$LINEAR_ENDPOINT" \
    -H "Authorization: $LINEAR_API_KEY" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg q "$query" --argjson v "$variables" '{query: $q, variables: $v}')"
}

resolve_model() {
  # Reads the issue JSON's labels for one matching "${COMPLEXITY_LABEL_PREFIX}<model>"
  # (e.g. "Complexity:Opus") and returns the model name lowercased. Falls back to
  # DEFAULT_MODEL if no complexity label is present. If more than one complexity
  # label is present, the first match wins and a warning is logged.
  local file="$1"
  local matches model

  matches=$(jq -r --arg prefix "$COMPLEXITY_LABEL_PREFIX" \
    '.labels.nodes[].name | select(startswith($prefix))' "$file")

  if [[ -z "$matches" ]]; then
    echo "$DEFAULT_MODEL"
    return 0
  fi

  local count
  count=$(echo "$matches" | wc -l | tr -d ' ')
  if [[ "$count" -gt 1 ]]; then
    log "WARNING: multiple complexity labels found ($(echo "$matches" | tr '\n' ',')), using the first"
  fi

  model=$(echo "$matches" | head -1 | sed "s/^${COMPLEXITY_LABEL_PREFIX}//" | tr '[:upper:]' '[:lower:]')
  echo "${model:-$DEFAULT_MODEL}"
}

get_label_id() {
  # Looks up a workspace label id by name. Returns empty string if not found.
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
  # Fetches the issue's CURRENT label ids live from Linear (not the queue
  # file's one-time snapshot, which goes stale the moment any prior mutation
  # in this run — e.g. claim_issue — changes the issue's labels).
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

# ---------------------------------------------------------------------------
# Step 1: fetch ai-ready issues and queue them locally
# ---------------------------------------------------------------------------

fetch_ready_issues() {
  log "Fetching issues labelled '${LINEAR_LABEL_READY}' from Linear"

  # Clear stale queue files from a prior run — otherwise an issue that's
  # since left ai-ready (claimed, merged, whatever) gets reprocessed anyway
  # just because its old queue file is still sitting on disk.
  rm -f "${QUEUE_DIR}"/*.json

  local query='
    query($label: String!) {
      issues(filter: { labels: { name: { eq: $label } }, state: { type: { neq: "completed" } } }) {
        nodes {
          id
          identifier
          title
          description
          branchName
          labels { nodes { id name } }
          comments { nodes { body createdAt } }
        }
      }
    }'

  local response
  response=$(linear_graphql "$query" "$(jq -n --arg label "$LINEAR_LABEL_READY" '{label: $label}')")

  if [[ "$(echo "$response" | jq -r '.errors // empty')" != "" ]]; then
    log "Linear API returned errors:"
    echo "$response" | jq '.errors' >&2
    exit 1
  fi

  local count
  count=$(echo "$response" | jq '.data.issues.nodes | length')
  log "Found ${count} ready issue(s)"

  echo "$response" | jq -c '.data.issues.nodes[]' | while read -r issue; do
    local identifier
    identifier=$(echo "$issue" | jq -r '.identifier')
    echo "$issue" > "${QUEUE_DIR}/${identifier}.json"
  done
}

# ---------------------------------------------------------------------------
# Step 2: claim an issue (swap label so a second run doesn't pick it up)
# ---------------------------------------------------------------------------

claim_issue() {
  local file="$1"
  local issue_id current_label_ids ready_id in_progress_id new_label_ids

  issue_id=$(jq -r '.id' "$file")
  current_label_ids=$(get_current_label_ids "$issue_id")
  ready_id=$(get_label_id "$LINEAR_LABEL_READY")
  in_progress_id=$(get_label_id "$LINEAR_LABEL_IN_PROGRESS")

  if [[ -z "$in_progress_id" ]]; then
    log "WARNING: label '${LINEAR_LABEL_IN_PROGRESS}' does not exist in Linear; skipping claim, relying on local queue only"
    return 0
  fi

  new_label_ids=$(jq -n --argjson current "$current_label_ids" --arg ready "$ready_id" --arg wip "$in_progress_id" \
    '($current - [$ready]) + [$wip] | unique')

  local mutation='
    mutation($id: String!, $labelIds: [String!]!) {
      issueUpdate(id: $id, input: { labelIds: $labelIds }) { success }
    }'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would claim issue ${issue_id} (labels -> ${new_label_ids})"
    return 0
  fi

  linear_graphql "$mutation" "$(jq -n --arg id "$issue_id" --argjson labelIds "$new_label_ids" '{id: $id, labelIds: $labelIds}')" \
    > /dev/null
}

post_comment() {
  local issue_id="$1"
  local body="$2"

  local mutation='
    mutation($issueId: String!, $body: String!) {
      commentCreate(input: { issueId: $issueId, body: $body }) { success }
    }'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would comment on ${issue_id}: ${body}"
    return 0
  fi

  linear_graphql "$mutation" "$(jq -n --arg id "$issue_id" --arg b "$body" '{issueId: $id, body: $b}')" \
    > /dev/null
}

set_final_label() {
  local file="$1"
  local target_label_name="$2"

  local issue_id current_label_ids wip_id target_id new_label_ids
  issue_id=$(jq -r '.id' "$file")
  current_label_ids=$(get_current_label_ids "$issue_id")
  wip_id=$(get_label_id "$LINEAR_LABEL_IN_PROGRESS")
  target_id=$(get_label_id "$target_label_name")

  if [[ -z "$target_id" ]]; then
    log "WARNING: label '${target_label_name}' does not exist in Linear; leaving labels as-is"
    return 0
  fi

  new_label_ids=$(jq -n --argjson current "$current_label_ids" --arg wip "$wip_id" --arg target "$target_id" \
    '($current - [$wip]) + [$target] | unique')

  local mutation='
    mutation($id: String!, $labelIds: [String!]!) {
      issueUpdate(id: $id, input: { labelIds: $labelIds }) { success }
    }'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would set final label '${target_label_name}' on ${issue_id}"
    return 0
  fi

  linear_graphql "$mutation" "$(jq -n --arg id "$issue_id" --argjson labelIds "$new_label_ids" '{id: $id, labelIds: $labelIds}')" \
    > /dev/null
}

# ---------------------------------------------------------------------------
# Step 2.5: give each issue its own worktree so parallel workers never share
# a working directory (git only allows one branch checked out per checkout)
# ---------------------------------------------------------------------------

worktree_path_for() {
  # Lowercased identifier as a sibling-directory name, e.g. QNS-11 -> qns-11,
  # matching the lowercase convention Linear's branch names already use.
  local identifier="$1"
  echo "${WORKTREE_DIR}/$(echo "$identifier" | tr '[:upper:]' '[:lower:]')"
}

setup_worktree() {
  # $1 = identifier, $2 = branch name. Echoes the worktree path on success,
  # returns non-zero (with nothing echoed) if the worktree couldn't be
  # created — caller is responsible for treating that as a failed issue.
  local identifier="$1"
  local branch_name="$2"
  local worktree_path
  worktree_path=$(worktree_path_for "$identifier")

  # Re-fetch immediately before every worktree creation, not just once at
  # the top of the whole run — this loop blocks on wait_for_merge between
  # issues, so main genuinely moves forward mid-run (the previous issue's PR
  # merging), and branching off a stale local origin/main ref here is what
  # produced merge-conflicting PRs (e.g. QNS-19 branching off the pre-QNS-17
  # /QNS-18 main and re-touching the same proto/avro files they'd already
  # changed).
  git -C "$REPO_DIR" fetch origin --prune >&2

  # Stale leftovers from a crashed prior run (registered worktree entries
  # for deleted dirs, or a stray directory from a run that never cleaned up).
  git -C "$REPO_DIR" worktree prune >&2
  if [[ -e "$worktree_path" ]]; then
    rm -rf "$worktree_path"
  fi

  if git -C "$REPO_DIR" show-ref --verify --quiet "refs/heads/${branch_name}"; then
    # Branch already exists locally (e.g. a prior run of this loop got
    # partway through) — reuse it rather than fail.
    git -C "$REPO_DIR" worktree add "$worktree_path" "$branch_name" >&2 || return 1
  else
    git -C "$REPO_DIR" worktree add "$worktree_path" -b "$branch_name" origin/main >&2 || return 1
  fi

  # A fresh worktree is a new directory as far as direnv's per-path allow-list
  # is concerned, even though its .envrc is identical to REPO_DIR's — trust it
  # explicitly. `direnv exec` (used later, at claude invocation time) is what
  # actually loads the nix devShell and runs `clj -X:deps prep`; this step
  # only authorizes that .envrc to run.
  if ! ( cd "$worktree_path" && direnv allow . ) >&2; then
    log "[${identifier}] 'direnv allow' failed in ${worktree_path}"
    return 1
  fi

  echo "$worktree_path"
}

remove_worktree() {
  local worktree_path="$1"
  git -C "$REPO_DIR" worktree remove "$worktree_path" >&2 2>&1 || \
    log "WARNING: couldn't remove worktree ${worktree_path}, leaving it for manual cleanup"
}

# ---------------------------------------------------------------------------
# Step 3.5: block until a PR is reviewed and merged before starting the next
# issue — the whole reason this loop is serial, not parallel.
# ---------------------------------------------------------------------------

wait_for_merge() {
  # $1 = queue file, $2 = PR URL. Returns 0 once merged, 1 if the PR was
  # closed without merging (caller should stop the run, not skip ahead).
  local file="$1"
  local pr_url="$2"
  local identifier issue_id
  identifier=$(jq -r '.identifier' "$file")
  issue_id=$(jq -r '.id' "$file")

  log "[${identifier}] Waiting for ${pr_url} to be reviewed and merged before starting the next issue (polling every ${WAIT_POLL_SECONDS}s; safe to Ctrl-C, nothing else is queued mid-issue)"

  while true; do
    # PR state is the ground truth for "closed without merging" — Linear's
    # state generally won't move on its own if nothing merged, so relying on
    # Linear alone here would hang forever on a rejected PR.
    local pr_state
    pr_state=$(gh pr view "$pr_url" --json state --jq '.state' 2>/dev/null || echo "")

    if [[ "$pr_state" == "CLOSED" ]]; then
      log "[${identifier}] ${pr_url} was closed without merging — stopping the run so you can look at it"
      post_comment "$issue_id" "Agent loop stopped: ${pr_url} was closed without merging."
      return 1
    fi

    if [[ "$pr_state" == "MERGED" ]]; then
      log "[${identifier}] ${pr_url} merged — continuing"
      return 0
    fi

    # Belt-and-suspenders: also trust Linear's own state directly, in case
    # its GitHub integration flips the issue to completed slightly ahead of
    # (or in some other way than) gh reporting MERGED.
    local issue_state_type
    issue_state_type=$(linear_graphql \
      'query($id: String!) { issue(id: $id) { state { type } } }' \
      "$(jq -n --arg id "$issue_id" '{id: $id}')" \
      | jq -r '.data.issue.state.type // empty')

    if [[ "$issue_state_type" == "completed" ]]; then
      log "[${identifier}] Linear shows this issue as completed — continuing"
      return 0
    fi

    sleep "$WAIT_POLL_SECONDS"
  done
}

cleanup_merged_branch() {
  # Deletes both the remote and local refs for a now-merged branch.
  # GitHub's "automatically delete head branches" is on for this repo, so
  # the remote delete is usually a harmless no-op by the time we get here
  # (tolerated below) — this mainly exists for the LOCAL ref, which GitHub's
  # setting can't touch, and as a fallback if that setting is ever off.
  # Best-effort throughout: a branch already gone is not an error.
  local branch_name="$1"
  git -C "$REPO_DIR" push origin --delete "$branch_name" >&2 2>&1 || \
    log "NOTE: remote branch ${branch_name} already gone (likely GitHub's auto-delete) or couldn't be deleted"
  git -C "$REPO_DIR" branch -D "$branch_name" >&2 2>&1 || \
    log "NOTE: local branch ${branch_name} already gone or couldn't be deleted"
}

clear_review_label() {
  # Removes LINEAR_LABEL_REVIEW (ai-in-review) once a PR has actually
  # merged. Linear's own issue state (flipped to "completed" by its GitHub
  # integration) is the authoritative "this is done" signal — the ai-* label
  # set only exists to drive THIS pipeline's queue, so once an issue is out
  # of the pipeline it shouldn't keep carrying a label implying it's still
  # mid-flight. Leaves every other label (Complexity:*, project labels, ...)
  # untouched.
  local file="$1"
  local issue_id current_label_ids review_id new_label_ids
  issue_id=$(jq -r '.id' "$file")
  review_id=$(get_label_id "$LINEAR_LABEL_REVIEW")

  if [[ -z "$review_id" ]]; then
    return 0
  fi

  current_label_ids=$(get_current_label_ids "$issue_id")
  new_label_ids=$(jq -n --argjson current "$current_label_ids" --arg review "$review_id" \
    '($current - [$review]) | unique')

  local mutation='
    mutation($id: String!, $labelIds: [String!]!) {
      issueUpdate(id: $id, input: { labelIds: $labelIds }) { success }
    }'

  if [[ "$DRY_RUN" == "true" ]]; then
    log "Would clear '${LINEAR_LABEL_REVIEW}' label now that this issue has merged"
    return 0
  fi

  linear_graphql "$mutation" "$(jq -n --arg id "$issue_id" --argjson labelIds "$new_label_ids" '{id: $id, labelIds: $labelIds}')" \
    > /dev/null
}

# ---------------------------------------------------------------------------
# Step 3: process a single issue (runs in its own worktree)
# ---------------------------------------------------------------------------

process_issue() {
  local file="$1"
  local identifier issue_id title plan branch_name log_file model

  identifier=$(jq -r '.identifier' "$file")
  issue_id=$(jq -r '.id' "$file")
  title=$(jq -r '.title' "$file")
  # Select by the "## Implementation plan" marker (same convention
  # linear-plan-loop.sh uses to detect an already-planned issue), not just
  # the newest comment — this script posts its own status comments (e.g.
  # "Agent run failed: ...") back onto the issue, and those would otherwise
  # become the newest comment and get mistaken for the plan on a retry.
  plan=$(jq -r '[.comments.nodes[] | select(.body | startswith("## Implementation plan"))] | sort_by(.createdAt) | last.body // ""' "$file")
  branch_name=$(jq -r '.branchName // empty' "$file")
  log_file="${LOG_DIR}/${identifier}.json"
  model=$(resolve_model "$file")

  log "[${identifier}] Resolved model: ${model}"

  log "[${identifier}] Claiming issue"
  claim_issue "$file"

  if [[ -z "$plan" ]]; then
    log "[${identifier}] No plan comment found, skipping"
    post_comment "$issue_id" "Agent loop skipped this issue: no implementation plan comment found."
    set_final_label "$file" "$LINEAR_LABEL_FAILED"
    return 1
  fi

  if [[ -z "$branch_name" ]]; then
    log "[${identifier}] No branchName returned by Linear, skipping"
    post_comment "$issue_id" "Agent loop skipped this issue: Linear did not return a recommended branch name."
    set_final_label "$file" "$LINEAR_LABEL_FAILED"
    return 1
  fi

  local worktree_path
  if [[ "$DRY_RUN" == "true" ]]; then
    worktree_path=$(worktree_path_for "$identifier")
    log "[dry-run] Would create worktree at ${worktree_path} on branch ${branch_name}"
  else
    log "[${identifier}] Setting up worktree (branch: ${branch_name})"
    if ! worktree_path=$(setup_worktree "$identifier" "$branch_name"); then
      log "[${identifier}] Failed to create worktree, skipping"
      post_comment "$issue_id" "Agent loop skipped this issue: couldn't create a git worktree for branch ${branch_name} (see runner logs)."
      set_final_label "$file" "$LINEAR_LABEL_FAILED"
      return 1
    fi
  fi

  local prompt_file
  prompt_file=$(mktemp)
  cat > "$prompt_file" <<EOF
Linear issue ${identifier}: ${title}

Implementation plan:
${plan}

You are already on an isolated branch (${branch_name}) checked out fresh off
origin/main in its own worktree — do not create or switch branches.

Implement the plan above. Follow the existing Polylith component boundaries
in this repo (com.repldriven.mono). Do not touch components outside the scope
described in the plan.

When implementation is complete, run /commit-and-pr to commit your changes and
open a pull request. Reference ${identifier} in the commit message and PR
description.

Finally, report the URL of the pull request you opened.
EOF

  local schema='{"type":"object","properties":{"pr_url":{"type":"string"}},"required":["pr_url"]}'

  log "[${identifier}] Running claude -p with model '${model}' (worktree: ${worktree_path})"
  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would run claude -p (model=${model}) from ${worktree_path} with prompt from ${prompt_file}"
    rm -f "$prompt_file"
    return 0
  fi

  (
    cd "$worktree_path"
    # direnv exec loads this worktree's devShell (clojure, zprint,
    # clj-kondo, ...) and runs `clj -X:deps prep` as a side effect of
    # loading .envrc, so claude and every Bash tool call it makes inherit a
    # fully-prepped environment — a plain `cd` here would not, since direnv's
    # shell hook only fires on interactive prompt render, never in a
    # non-interactive script like this one.
    cat "$prompt_file" | direnv exec . claude -p \
      --model "$model" \
      --allowedTools "$CLAUDE_ALLOWED_TOOLS" \
      --permission-mode acceptEdits \
      --output-format json \
      --json-schema "$schema" \
      > "$log_file"
  )
  rm -f "$prompt_file"

  if [[ "$(jq -r '.is_error // false' "$log_file")" == "true" ]]; then
    log "[${identifier}] Claude run reported an error"
    post_comment "$issue_id" "Agent run failed: $(jq -r '.result' "$log_file")"
    set_final_label "$file" "$LINEAR_LABEL_FAILED"
    log "[${identifier}] Leaving worktree at ${worktree_path} for inspection"
    return 1
  fi

  local pr_url
  pr_url=$(jq -r '.structured_output.pr_url // empty' "$log_file")

  if [[ -z "$pr_url" ]]; then
    # /commit-and-pr can succeed (push + open a real PR) even when claude -p's
    # own final structured-output response fails to carry the pr_url field —
    # seen in practice on QNS-19: the PR existed on GitHub seconds before this
    # branch fired. Check GitHub directly before declaring the run failed;
    # trusting structured_output alone here caused the loop to skip
    # wait_for_merge entirely and race ahead to the next issue against a main
    # that hadn't actually picked up this one's changes yet.
    pr_url=$(gh pr list --repo repldriven/queenswood --head "$branch_name" --state open --json url --jq '.[0].url // empty' 2>/dev/null || true)
    if [[ -n "$pr_url" ]]; then
      log "[${identifier}] Claude reported no PR URL, but found one on GitHub for ${branch_name}: ${pr_url}"
    fi
  fi

  if [[ -z "$pr_url" ]]; then
    log "[${identifier}] Claude finished but reported no PR URL, treating as failed"
    post_comment "$issue_id" "Agent run completed but did not report a PR URL. Check the branch ${branch_name} manually."
    set_final_label "$file" "$LINEAR_LABEL_FAILED"
    log "[${identifier}] Leaving worktree at ${worktree_path} for inspection"
    return 1
  fi

  post_comment "$issue_id" "Agent implemented this issue: ${pr_url}"
  set_final_label "$file" "$LINEAR_LABEL_REVIEW"

  # Changes are committed and pushed by /commit-and-pr at this point, so the
  # worktree is safe to reclaim; the branch itself is NOT deleted here — it
  # still needs to survive review, and is only cleaned up once the caller's
  # wait_for_merge confirms it actually merged.
  remove_worktree "$worktree_path"

  log "[${identifier}] Done -> ${pr_url}"
  echo "$pr_url"
}

# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if [[ "${1:-}" == "--process" ]]; then
  process_issue "$2"
  exit $?
fi

fetch_ready_issues

queued=("${QUEUE_DIR}"/*.json)
if [[ ! -e "${queued[0]:-}" ]]; then
  log "No issues to process"
  exit 0
fi

log "Processing ${#queued[@]} issue(s) serially — each one waits for review + merge before the next starts"

for f in "${queued[@]}"; do
  identifier=$(jq -r '.identifier' "$f")
  branch_name=$(jq -r '.branchName // empty' "$f")

  log "=== ${identifier} ==="

  pr_url=""
  if pr_url=$(process_issue "$f"); then
    if [[ "$DRY_RUN" == "true" || -z "$pr_url" ]]; then
      continue
    fi
    if ! wait_for_merge "$f" "$pr_url"; then
      log "Stopping run — ${identifier} needs attention before continuing (see the comment on the issue)"
      exit 1
    fi
    clear_review_label "$f"
    if [[ -n "$branch_name" ]]; then
      cleanup_merged_branch "$branch_name"
    fi
  else
    log "[${identifier}] Failed before opening a PR — moving on to the next queued issue"
  fi
done

log "Run complete. Logs in ${LOG_DIR}"
