# Print the seed project id, found by label.
[private]
_seed-project:
    #!/usr/bin/env zsh
    set -e
    ID=$(just _gcp-project "labels.queenswood-tier=seed")
    if [[ -z "$ID" ]]; then
      echo "no seed project; run 'just seed-create'" >&2
      exit 1
    fi
    printf '%s\n' "$ID"

# Print the seed service account's email.
[private]
_seed-sa:
    @printf 'sa-{{ CODE }}-boot@%s.iam.gserviceaccount.com\n' "$(just _seed-project)"

# Check the organisation, billing and rights held before bootstrapping.
seed-preflight:
    #!/usr/bin/env zsh
    set -e
    ME=$(gcloud config get-value account 2>/dev/null || true)
    if [[ -z "$ME" || "$ME" == "(unset)" ]]; then
      echo "not authenticated -- run: gcloud auth login" >&2
      exit 1
    fi
    echo "account           $ME"

    ORG=$(just _gcp-org-id)
    CUSTOMER=$(gcloud organizations describe "$ORG" \
                 --format='value(owner.directoryCustomerId)')
    echo "organization      $ORG"
    echo "cloud identity    customer $CUSTOMER"

    BLOCKS=()

    BILLING=$(gcloud billing accounts list --filter='OPEN=True' \
                --format='value(ACCOUNT_ID)' 2>/dev/null | tr '\n' ' ' || true)
    if [[ -z "${BILLING// }" ]]; then
      echo "billing           NONE open"
      BLOCKS+=("no open billing account -- create one in the console, no API exists")
    else
      echo "billing           $BILLING"
    fi

    PROJECT=$(gcloud config get-value project 2>/dev/null || true)
    if [[ -n "$PROJECT" && "$PROJECT" != "(unset)" ]]; then
      echo "active project    $PROJECT (quota attribution only)"
    fi

    echo
    echo "organization roles bound directly to $ME:"
    if ROLES=$(gcloud organizations get-iam-policy "$ORG" \
                 --flatten='bindings[].members' \
                 --filter="bindings.members:user:$ME" \
                 --format='value(bindings.role)' 2>/dev/null); then
      if [[ -z "${ROLES// }" ]]; then
        echo "  none bound directly"
      else
        printf '%s\n' "$ROLES" | sed 's/^/  /'
      fi
      echo "  (domain-wide grants are not listed here -- they name the domain)"
    else
      echo "  not readable by this account, which blocks nothing"
      echo "  (Browser carries no organizations.getIamPolicy)"
    fi

    echo
    echo "to create a folder, one of these must hold on the parent:"
    echo "  roles/resourcemanager.folderAdmin"
    echo "  roles/resourcemanager.folderCreator + folderIamAdmin"
    echo "granting them at the organization needs:"
    echo "  roles/resourcemanager.organizationAdmin"

    echo
    echo "parents a folder could be created under:"
    printf '  organizations/%s\n' "$ORG"
    gcloud resource-manager folders list --organization="$ORG" \
      --format='value(name,displayName)' 2>/dev/null \
      | sed 's/^/  /' || true

    if (( ${#BLOCKS[@]} )); then
      echo
      echo "BLOCKED:"
      for B in $BLOCKS; do
        echo "  $B"
      done
      exit 1
    fi
    echo
    echo "nothing blocking. next: just seed-create"

# Create the seed project and the seed identity in it.
seed-create billing="" yes="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id)
    ME=$(gcloud config get-value account)

    BILLING="{{ billing }}"
    if [[ -z "$BILLING" ]]; then
      BILLING=$(gcloud billing accounts list --filter='OPEN=True' \
                  --format='value(ACCOUNT_ID)' | head -1 || true)
    fi
    if [[ -z "$BILLING" ]]; then
      echo "no open billing account; pass one as the second argument" >&2
      exit 1
    fi

    # Not silenced: a swallowed denial reads as "none exists" and mints
    # a second seed project, consuming an id for ever.
    if ! OUT=$(gcloud projects list --filter="labels.queenswood-tier=seed" \
                 --format='value(projectId)' 2>&1); then
      echo "cannot list projects:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi
    PROJECT=$(printf '%s\n' "$OUT" | head -1)
    if [[ -n "$PROJECT" ]]; then
      PROJECT_ACT="reuse"
    else
      PROJECT="prj-b-seed-$(openssl rand -hex 3)"
      PROJECT_ACT="create under organization $ORG, billed to $BILLING"
    fi
    DOMAIN=$(just _gcp-domain)

    echo "organization    $ORG"
    echo "billing         $BILLING"
    echo "as              $ME"
    echo "seed project    $PROJECT ($PROJECT_ACT)"
    echo "identity        sa-{{ CODE }}-boot@${PROJECT}.iam.gserviceaccount.com"
    echo "impersonated by $(just _gcp-member "{{ PLATFORM_ADMIN }}" "$DOMAIN")"
    echo
    if [[ -z "{{ yes }}" ]]; then
      printf 'proceed? [y/N] '
      read -r REPLY || REPLY=""
      if [[ "$REPLY" != [yY]* ]]; then
        echo "abandoned"
        exit 1
      fi
    fi

    if [[ "$PROJECT_ACT" == "reuse" ]]; then
      echo ">>> reusing seed project $PROJECT"
    else
      echo ">>> creating project $PROJECT under organization $ORG"
      gcloud projects create "$PROJECT" --name=prj-b-seed \
        --organization="$ORG" --labels=queenswood-tier=seed
      echo ">>> linking billing $BILLING"
      gcloud billing projects link "$PROJECT" \
        --billing-account="$BILLING" >/dev/null
    fi

    echo ">>> enabling the APIs the identity needs"
    gcloud services enable --project="$PROJECT" \
      cloudasset.googleapis.com \
      cloudresourcemanager.googleapis.com \
      cloudbilling.googleapis.com \
      iam.googleapis.com \
      iamcredentials.googleapis.com \
      serviceusage.googleapis.com

    SA="sa-{{ CODE }}-boot@${PROJECT}.iam.gserviceaccount.com"
    if ! gcloud iam service-accounts describe "$SA" \
           --project="$PROJECT" >/dev/null 2>&1; then
      echo ">>> creating service account $SA"
      gcloud iam service-accounts create sa-{{ CODE }}-boot --project="$PROJECT" \
        --display-name="sa-{{ CODE }}-boot" \
        --description="Creates the management project"
    fi

    echo ">>> granting roles/billing.user on billing account $BILLING"
    if ! ERR=$(gcloud billing accounts add-iam-policy-binding "$BILLING" \
                 --member="serviceAccount:${SA}" --role=roles/billing.user \
                 2>&1 >/dev/null); then
      echo "could not grant billing.user to $SA on $BILLING:" >&2
      printf '%s\n' "$ERR" | sed 's/^/  /' >&2
      echo "if $SA was just created, run this again -- it is idempotent" >&2
      exit 1
    fi

    PLATFORM_ADMIN=$(just _gcp-member "{{ PLATFORM_ADMIN }}" "$DOMAIN")
    echo ">>> letting $PLATFORM_ADMIN impersonate $SA"
    gcloud iam service-accounts add-iam-policy-binding "$SA" \
      --project="$PROJECT" --member="$PLATFORM_ADMIN" \
      --role=roles/iam.serviceAccountTokenCreator >/dev/null

    STRAGGLERS=$(gcloud iam service-accounts get-iam-policy "$SA" \
                   --project="$PROJECT" --flatten='bindings[].members' \
                   --filter="bindings.role:roles/iam.serviceAccountTokenCreator AND bindings.members:user:*" \
                   --format='value(bindings.members)' 2>/dev/null || true)
    for member in ${(f)STRAGGLERS}; do
      echo ">>> removing direct binding for $member"
      gcloud iam service-accounts remove-iam-policy-binding "$SA" \
        --project="$PROJECT" --member="$member" \
        --role=roles/iam.serviceAccountTokenCreator >/dev/null
    done

    echo
    echo ">>> project  $PROJECT"
    echo ">>> identity $SA"
    echo ">>> act as it by joining $PLATFORM_ADMIN, and leave after"
    echo ">>> if it holds no organization roles yet, an organization admin"
    echo ">>> runs: just seed-grant-org-roles"

# Grant the seed identity the roles it needs at the organisation.
seed-grant-org-roles yes="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id)
    SA=$(just _seed-sa)
    ME=$(gcloud config get-value account)

    ROLES=(
      roles/resourcemanager.folderCreator
      roles/resourcemanager.folderIamAdmin
      roles/resourcemanager.projectCreator
      roles/orgpolicy.policyAdmin
      roles/cloudasset.viewer
      roles/browser
    )

    echo "organization    $ORG"
    echo "as              $ME"
    echo "granting to     $SA"
    for role in $ROLES; do
      echo "                $role"
    done
    echo
    if [[ -z "{{ yes }}" ]]; then
      printf 'proceed? [y/N] '
      read -r REPLY || REPLY=""
      if [[ "$REPLY" != [yY]* ]]; then
        echo "abandoned"
        exit 1
      fi
    fi

    for role in $ROLES; do
      echo ">>> granting $role on organization $ORG"
      gcloud organizations add-iam-policy-binding "$ORG" \
        --member="serviceAccount:${SA}" --role="$role" \
        --condition=None >/dev/null
    done
    echo
    echo ">>> $SA can now create the folder"

# Impersonate the seed identity, via ADC (opens a browser).
seed-impersonate:
    #!/usr/bin/env zsh
    set -e
    SA=$(just _seed-sa)
    echo ">>> ADC will impersonate $SA"
    gcloud auth application-default login --impersonate-service-account="$SA"

# Revoke the impersonation. The credential is destroyed.
seed-impersonate-revoke:
    @gcloud auth application-default revoke --quiet

# Close the seed identity: no impersonation, no org-wide creation.
seed-close yes="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id)
    SA=$(just _seed-sa)
    PROJECT=$(just _seed-project)
    DOMAIN=$(just _gcp-domain)
    PLATFORM_ADMIN=$(just _gcp-member "{{ PLATFORM_ADMIN }}" "$DOMAIN")

    REVOKE=(
      roles/resourcemanager.folderCreator
      roles/resourcemanager.folderIamAdmin
      roles/resourcemanager.projectCreator
    )

    echo "organization    $ORG"
    echo "identity        $SA"
    echo "revoking impersonation by"
    echo "                $PLATFORM_ADMIN"
    echo "revoking at the organization"
    for role in $REVOKE; do
      echo "                $role"
    done
    echo "keeping         orgpolicy.policyAdmin, cloudasset.viewer, browser"
    echo
    if [[ -z "{{ yes }}" ]]; then
      printf 'proceed? [y/N] '
      read -r REPLY || REPLY=""
      if [[ "$REPLY" != [yY]* ]]; then
        echo "abandoned"
        exit 1
      fi
    fi

    echo ">>> removing impersonation by $PLATFORM_ADMIN"
    gcloud iam service-accounts remove-iam-policy-binding "$SA" \
      --project="$PROJECT" --member="$PLATFORM_ADMIN" \
      --role=roles/iam.serviceAccountTokenCreator >/dev/null
    for role in $REVOKE; do
      echo ">>> revoking $role on organization $ORG"
      gcloud organizations remove-iam-policy-binding "$ORG" \
        --member="serviceAccount:${SA}" --role="$role" \
        --condition=None >/dev/null
    done
    echo
    echo ">>> $SA can no longer be impersonated or create anything"

# Reopen impersonation after a close, without needing billing admin.
seed-open yes="":
    #!/usr/bin/env zsh
    set -e
    SA=$(just _seed-sa)
    PROJECT=$(just _seed-project)
    DOMAIN=$(just _gcp-domain)
    PLATFORM_ADMIN=$(just _gcp-member "{{ PLATFORM_ADMIN }}" "$DOMAIN")

    echo "identity        $SA"
    echo "impersonated by $PLATFORM_ADMIN"
    echo
    if [[ -z "{{ yes }}" ]]; then
      printf 'proceed? [y/N] '
      read -r REPLY || REPLY=""
      if [[ "$REPLY" != [yY]* ]]; then
        echo "abandoned"
        exit 1
      fi
    fi

    gcloud iam service-accounts add-iam-policy-binding "$SA" \
      --project="$PROJECT" --member="$PLATFORM_ADMIN" \
      --role=roles/iam.serviceAccountTokenCreator >/dev/null
    echo ">>> $PLATFORM_ADMIN may impersonate $SA again"

# Show the seed identity, its rights, and who may impersonate it.
seed-status:
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id)
    PROJECT=$(just _seed-project)
    SA=$(just _seed-sa)
    echo "project   $PROJECT"
    echo "identity  $SA"
    echo "organization roles:"
    if OUT=$(gcloud organizations get-iam-policy "$ORG" \
               --flatten='bindings[].members' \
               --filter="bindings.members:serviceAccount:$SA" \
               --format='value(bindings.role)' 2>/dev/null); then
      printf '%s\n' "${OUT:-  none}" | sed 's/^/  /'
    else
      echo "  not readable by this account"
    fi

    echo "may impersonate it:"
    if OUT=$(gcloud iam service-accounts get-iam-policy "$SA" \
               --project="$PROJECT" --flatten='bindings[].members' \
               --filter="bindings.role:roles/iam.serviceAccountTokenCreator" \
               --format='value(bindings.members)' 2>/dev/null); then
      printf '%s\n' "${OUT:-  nobody}" | sed 's/^/  /'
    else
      echo "  not readable by this account"
    fi

# Show whether the seed identity is being impersonated.
seed-impersonate-status:
    #!/usr/bin/env zsh
    set -e
    F="$HOME/.config/gcloud/application_default_credentials.json"
    if [[ ! -f "$F" ]]; then
      echo "no ADC"
      exit 0
    fi
    TYPE=$(grep -o '"type"[^,]*' "$F" | head -1 | sed 's/.*: *"//; s/"//')
    SA=$(grep -o 'serviceAccounts/[^:]*' "$F" | head -1 | sed 's|serviceAccounts/||')
    echo "type         ${TYPE:-unknown}"
    echo "impersonates ${SA:-nothing}"
    if ERR=$(gcloud auth application-default print-access-token 2>&1 >/dev/null); then
      echo "token        mints"
    else
      echo "token        FAILS -- run 'just seed-impersonate'"
      printf '%s\n' "$ERR" | sed 's/^/  /'
      exit 1
    fi
