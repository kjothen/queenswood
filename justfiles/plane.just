# Print the management project's id.
[private]
_mgmt-project:
    #!/usr/bin/env zsh
    set -e
    ID=$(just _gcp-project "projectId ~ ^prj-{{ CODE }}-c-mgmt-")
    if [[ -z "$ID" ]]; then
      echo "no management project for {{ CODE }}" >&2
      exit 1
    fi
    printf '%s\n' "$ID"

# Print the recovery project's id, or nothing where the installation
# has none.
[private]
_recovery-project:
    @just _gcp-project "projectId ~ ^prj-{{ CODE }}-c-recovery-"

# Print the platform identity's email address.
plane-identity:
    @printf 'sa-{{ CODE }}-platform@%s.iam.gserviceaccount.com\n' "$(just _mgmt-project)"

# Let the platform identity bill the projects it creates (as a billing admin).
plane-billing-role billing="":
    #!/usr/bin/env zsh
    set -e
    SA=$(just plane-identity)

    BILLING="{{ billing }}"
    if [[ -z "$BILLING" ]]; then
      BILLING=$(just _gcp-billing-account "$(just _seed-sa)")
    fi

    echo ">>> granting roles/billing.user on $BILLING"
    echo ">>> to $SA"
    if ! gcloud billing accounts add-iam-policy-binding "$BILLING" \
           --member="serviceAccount:${SA}" --role=roles/billing.user \
           >/dev/null; then
      echo "could not grant billing.user on $BILLING" >&2
      echo "needs roles/billing.admin there -- held directly by the" >&2
      echo "account's own administrator, or through {{ ORG_BILLING_ADMIN_GROUP }}" >&2
      exit 1
    fi
    echo ">>> granted. The platform identity can now bill the projects"
    echo ">>> it creates, which is what an instance needs."

# Add a kubectl context for the management cluster, named MGMT_CTX.
plane-ctx project="":
    #!/usr/bin/env zsh
    set -e
    CLUSTER="{{ CODE }}-c-mgmt"

    PROJECT="{{ project }}"
    if [[ -z "$PROJECT" ]]; then
      PROJECT=$(just _mgmt-project)
    fi

    ZONE=$(gcloud container clusters list --project="$PROJECT" \
             --filter="name=$CLUSTER" --format='value(location)' | head -1)
    if [[ -z "$ZONE" ]]; then
      echo "no cluster $CLUSTER in $PROJECT" >&2
      exit 1
    fi

    # get-credentials makes its context current, which would silently
    # retarget a shell working against the throwaway plane -- so the
    # current one is captured and put back.
    WAS=$(kubectl config current-context 2>/dev/null || true)

    gcloud container clusters get-credentials "$CLUSTER" \
      --zone="$ZONE" --project="$PROJECT"
    kubectl config rename-context \
      "gke_${PROJECT}_${ZONE}_${CLUSTER}" "{{ MGMT_CTX }}" >/dev/null 2>&1 || true

    if [[ -n "$WAS" && "$WAS" != "gke_${PROJECT}_${ZONE}_${CLUSTER}" ]]; then
      kubectl config use-context "$WAS" >/dev/null
    fi

    echo
    echo ">>> context {{ MGMT_CTX }} -> $CLUSTER in $PROJECT ($ZONE)"
    echo ">>> still on ${WAS:-none}; use --context {{ MGMT_CTX }}"
    echo ">>> fetching credentials needs only container.viewer, which you"
    echo ">>> hold. Calling the API needs container.admin: join"
    echo ">>> grp-gcp-{{ CODE }}-cluster-admin for the task, and leave."

# Show what the platform identity may do, and where.
plane-status:
    #!/usr/bin/env zsh
    set -e
    SA=$(just plane-identity)
    PROJECT=$(just _mgmt-project)
    echo "identity  $SA"
    echo "project   $PROJECT"

    BILLING=$(just _gcp-billing-account "$(just _seed-sa)" 2>/dev/null || true)
    echo "billing role:"
    if [[ -z "$BILLING" ]]; then
      echo "  no billing account readable from here"
    elif OUT=$(gcloud billing accounts get-iam-policy "$BILLING" \
                 --flatten='bindings[].members' \
                 --filter="bindings.members:serviceAccount:$SA" \
                 --format='value(bindings.role)' 2>/dev/null); then
      printf '%s\n' "${OUT:-  none -- run 'just plane-billing-role'}" \
        | sed 's/^/  /'
    else
      echo "  not readable by this account"
    fi

    echo "folder roles:"
    FOLDER=$(gcloud resource-manager folders list \
               --organization="$(just _gcp-org-id)" \
               --filter="displayName=fldr-{{ CODE }}" \
               --format='value(name)' 2>/dev/null | head -1 || true)
    if [[ -z "$FOLDER" ]]; then
      echo "  no folder fldr-{{ CODE }}"
    elif OUT=$(gcloud resource-manager folders get-iam-policy "$FOLDER" \
                 --flatten='bindings[].members' \
                 --filter="bindings.members:serviceAccount:$SA" \
                 --format='value(bindings.role)' 2>/dev/null); then
      printf '%s\n' "${OUT:-  none}" | sed 's/^/  /'
    else
      echo "  not readable by this account"
    fi

    echo "management project roles:"
    if [[ -z "$PROJECT" ]]; then
      echo "  no management project"
    elif OUT=$(gcloud projects get-iam-policy "$PROJECT" \
                 --flatten='bindings[].members' \
                 --filter="bindings.members:serviceAccount:$SA" \
                 --format='value(bindings.role)' 2>/dev/null); then
      printf '%s\n' "${OUT:-  none}" | sed 's/^/  /'
    else
      echo "  not readable by this account"
    fi
