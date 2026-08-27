# Delete orphan container versions from GHCR (multi-arch safe)
gh-prune-untagged:
    #!/usr/bin/env zsh
    set -e
    scopes=$(gh api --include / 2>&1 | awk -F': ' 'tolower($1)=="x-oauth-scopes"{print $2}')
    if [[ "$scopes" != *delete:packages* ]]; then
      echo "gh token missing delete:packages. Run:"
      echo "  gh auth refresh -h github.com -s delete:packages,read:packages"
      exit 1
    fi
    OWNER=$(gh repo view --json owner --jq '.owner.login')
    TYPE=$(gh api "/users/$OWNER" --jq '.type' 2>/dev/null || echo "User")
    if [[ $TYPE == "Organization" ]]; then
      DELETE_BASE="/orgs/$OWNER/packages/container"
    else
      DELETE_BASE="/user/packages/container"
    fi
    gh api "/users/$OWNER/packages?package_type=container" --paginate \
        --jq '.[].name' | while read -r pkg; do
      # Per-platform manifests referenced by a tagged index are "untagged"
      # in GHCR; deleting them breaks the index. Build a keep set first.
      # The registry path takes the name as-is; the REST API needs it
      # percent-encoded, or a namespaced package 404s as "not found".
      enc="${pkg//\//%2F}"
      regtoken=$(curl -sL "https://ghcr.io/token?service=ghcr.io&scope=repository:$OWNER/$pkg:pull" | jq -r .token)
      keep=$(gh api "/users/$OWNER/packages/container/$enc/versions" --paginate \
              --jq '.[] | select(.metadata.container.tags != []) | .name' \
              | while read -r digest; do
                  curl -sL -H "Authorization: Bearer $regtoken" \
                    -H "Accept: application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json" \
                    "https://ghcr.io/v2/$OWNER/$pkg/manifests/$digest" \
                    | jq -r '.manifests[]?.digest // empty'
                done | sort -u)
      to_delete=$(gh api "/users/$OWNER/packages/container/$enc/versions" --paginate \
                    --jq '.[] | select(.metadata.container.tags == []) | "\(.id) \(.name)"' \
                  | while read -r id name; do
                      echo "$keep" | grep -qFx "$name" || echo "$id"
                    done)
      n=$(echo -n "$to_delete" | grep -c '^' || true)
      if [[ $n -eq 0 ]]; then
        printf "  %-45s 0\n" "$pkg"
        continue
      fi
      printf "  %-45s %s" "$pkg" "$n"
      for id in ${(f)to_delete}; do
        gh api -X DELETE "$DELETE_BASE/$enc/versions/$id" --silent || true
        printf "."
      done
      printf "\n"
    done

# Squash-merge the current branch's PR, bypassing the review rule as org admin
gh-merge:
    #!/usr/bin/env zsh
    set -e
    # No PR number: gh resolves it from the checked-out branch, which is the
    # one the editor is showing.
    pr=$(gh pr view --json number,baseRefName,state,title)
    num=$(jq -r .number <<<"$pr")
    base=$(jq -r .baseRefName <<<"$pr")
    state=$(jq -r .state <<<"$pr")
    if [[ "$state" != "OPEN" ]]; then
      echo "PR #$num is $state, nothing to merge."
      exit 1
    fi
    # A PR stacked on another branch merges its parent's commits too, and
    # strands the rest when the parent lands separately.
    if [[ "$base" != "main" ]]; then
      echo "PR #$num targets '$base', not main. Retarget it before merging:"
      echo "  gh pr edit $num --base main"
      exit 1
    fi
    echo "Merging #$num — $(jq -r .title <<<"$pr")"
    # --squash because the org ruleset requires linear history; --admin to
    # bypass the review rule, which a solo author cannot satisfy.
    gh pr merge "$num" --squash --admin
    # The squash commit's sha, seven characters, so it reads against the
    # REV column in docs/recipes/infra/argocd-apps.md without trimming it
    # by eye. Emptiness is handled below rather than by set -e.
    sha=$(gh pr view "$num" --json mergeCommit --jq '.mergeCommit.oid' 2>/dev/null) || true
    if [[ -n "$sha" && "$sha" != "null" ]]; then
      echo "Merged #$num into main as ${sha:0:7}."
    else
      echo "Merged #$num into main."
    fi
