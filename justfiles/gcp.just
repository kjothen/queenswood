# =========================================================================
# Google Cloud, from the foundation up.
#
# All new GCP work lands here, and anything still needed from
# cloud.just moves here rather than being called across -- cloud.just is
# the previous generation and shrinks as its recipes are replaced by
# managed resources. See docs/recipes/cloud-foundation.md.
# =========================================================================

# Group names. Roles bind to these and never move; membership does. A
# name says who is in the group, not what one of its bindings grants --
# gcp-platform-operators holds two roles at two scopes, and calling it
# after either would undersell what impersonation reaches.
ADMINS_GROUP := "gcp-organization-admins"
BILLING_GROUP := "gcp-billing-admins"
OPERATORS_GROUP := "gcp-platform-operators"

# Print the organisation's primary domain, which is its display name.
[private]
_gcp-domain org="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
    gcloud organizations describe "$ORG" --format='value(displayName)'

# Print the Cloud Identity customer id, e.g. C01k1e9nw.
[private]
_gcp-customer-id org="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
    gcloud organizations describe "$ORG" \
      --format='value(owner.directoryCustomerId)'

# Print the bootstrap project id, found by label.
[private]
_gcp-bootstrap-project:
    #!/usr/bin/env zsh
    set -e
    ID=$(gcloud projects list --filter="labels.queenswood-tier=bootstrap" \
           --format='value(projectId)' 2>/dev/null | head -1 || true)
    if [[ -z "$ID" ]]; then
      echo "no bootstrap project; run 'just gcp-bootstrap-identity'" >&2
      exit 1
    fi
    printf '%s\n' "$ID"

# Print the bootstrap service account's email.
[private]
_gcp-bootstrap-sa:
    @printf 'bootstrap@%s.iam.gserviceaccount.com\n' "$(just _gcp-bootstrap-project)"

# Print the organisation id, discovering it when only one is visible.
[private]
_gcp-org-id org="":
    #!/usr/bin/env zsh
    set -e
    if [[ -n "{{ org }}" ]]; then
      # Checked rather than trusted: an id from elsewhere authenticates
      # fine and then 403s on every call, which surfaces as "no
      # bindings" rather than "wrong organization".
      if ! gcloud organizations describe "{{ org }}" >/dev/null 2>&1; then
        echo "organization {{ org }} is not readable by $(gcloud config get-value account 2>/dev/null)" >&2
        exit 1
      fi
      printf '%s\n' "{{ org }}"
      exit 0
    fi
    ORGS=$(gcloud organizations list --format='value(ID)' 2>/dev/null || true)
    COUNT=$(printf '%s' "$ORGS" | grep -c . || true)
    if [[ "$COUNT" -eq 0 ]]; then
      echo "no organization visible to $(gcloud config get-value account 2>/dev/null)" >&2
      echo "an organization comes from Cloud Identity, not from gcloud --" >&2
      echo "see docs/recipes/cloud-account.md" >&2
      exit 1
    fi
    if [[ "$COUNT" -ne 1 ]]; then
      echo "$COUNT organizations visible; pass one, e.g. just gcp-preflight <id>:" >&2
      gcloud organizations list >&2
      exit 1
    fi
    printf '%s\n' "$ORGS"

# Report the organisation, billing and rights held (takes an org id if ambiguous).
gcp-preflight org="":
    #!/usr/bin/env zsh
    set -e

    ME=$(gcloud config get-value account 2>/dev/null || true)
    if [[ -z "$ME" || "$ME" == "(unset)" ]]; then
      echo "not authenticated -- run: gcloud auth login" >&2
      exit 1
    fi
    echo "account           $ME"

    ORG=$(just _gcp-org-id "{{ org }}")
    SOURCE="discovered"
    if [[ -n "{{ org }}" ]]; then
      SOURCE="given"
    fi
    # Unsuppressed on purpose: a 403 here means the wrong organization,
    # and rendering that as an empty field is how it gets missed.
    CUSTOMER=$(gcloud organizations describe "$ORG" \
                 --format='value(owner.directoryCustomerId)')
    echo "organization      $ORG ($SOURCE)"
    echo "cloud identity    customer $CUSTOMER"

    BILLING=$(gcloud billing accounts list --filter='OPEN=True' \
                --format='value(ACCOUNT_ID)' 2>/dev/null | tr '\n' ' ' || true)
    if [[ -z "${BILLING// }" ]]; then
      echo "billing           NONE open -- create one in the console, no API exists"
    else
      echo "billing           $BILLING"
    fi

    # No foundation resource lives in it, but gcloud attaches it to some
    # calls as the quota project, so a stale one denies commands that
    # have nothing to do with projects.
    PROJECT=$(gcloud config get-value project 2>/dev/null || true)
    if [[ -n "$PROJECT" && "$PROJECT" != "(unset)" ]]; then
      echo "active project    $PROJECT (quota attribution only)"
    fi

    # Domain ownership is deliberately not checked here. The only thing
    # that needs it is creating a public DNS zone, which happens per
    # instance and long after this, and every read-only way to ask
    # wants an enabled API on a project that does not exist yet.

    # Direct bindings only. A role held through a group does not appear
    # here, so an absent line is a prompt to check rather than proof.
    echo
    echo "organization roles bound directly to $ME:"
    # Not fatal, unlike the describe above. Organization Viewer does not
    # include getIamPolicy, so the operating user legitimately cannot
    # read this -- and a denial must never render as "(none)".
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
      echo "  (Organization Viewer excludes getIamPolicy)"
    fi

    # What the two paths need, stated rather than asserted, because the
    # bootstrap identity is granted these later -- not the operator.
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

# =========================================================================
# The bootstrap identity
#
# Two recipes because two accounts are involved. The operator owns the
# billing account and can create projects, but only an organisation
# admin can grant roles at the organisation -- which is the whole of
# what break-glass is for.
# =========================================================================

# Create the bootstrap identity and your right to impersonate it (as the operator).
gcp-bootstrap-identity org="" billing="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
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

    PROJECT=$(gcloud projects list --filter="labels.queenswood-tier=bootstrap" \
                --format='value(projectId)' 2>/dev/null | head -1 || true)
    if [[ -n "$PROJECT" ]]; then
      echo ">>> reusing bootstrap project $PROJECT"
    else
      PROJECT="queenswood-bootstrap-$(openssl rand -hex 3)"
      echo ">>> creating project $PROJECT under organization $ORG"
      gcloud projects create "$PROJECT" --name=queenswood-bootstrap \
        --organization="$ORG" --labels=queenswood-tier=bootstrap
      echo ">>> linking billing $BILLING"
      gcloud billing projects link "$PROJECT" \
        --billing-account="$BILLING" >/dev/null
    fi

    echo ">>> enabling the APIs the identity needs"
    gcloud services enable --project="$PROJECT" \
      cloudresourcemanager.googleapis.com \
      cloudbilling.googleapis.com \
      iam.googleapis.com \
      iamcredentials.googleapis.com \
      serviceusage.googleapis.com

    SA="bootstrap@${PROJECT}.iam.gserviceaccount.com"
    if ! gcloud iam service-accounts describe "$SA" \
           --project="$PROJECT" >/dev/null 2>&1; then
      echo ">>> creating service account $SA"
      gcloud iam service-accounts create bootstrap --project="$PROJECT" \
        --display-name="Queenswood bootstrap" \
        --description="Creates the management project; see docs/recipes/cloud-foundation.md"
    fi

    echo ">>> granting roles/billing.user on billing account $BILLING"
    gcloud billing accounts add-iam-policy-binding "$BILLING" \
      --member="serviceAccount:${SA}" --role=roles/billing.user >/dev/null

    # The group, never a person. Membership is then the whole of who can
    # act as this identity, and removing it is one revocation in one
    # place rather than an edit to this policy.
    DOMAIN=$(just _gcp-domain "{{ org }}")
    OPERATORS="{{ OPERATORS_GROUP }}@${DOMAIN}"
    echo ">>> letting $OPERATORS impersonate $SA"
    gcloud iam service-accounts add-iam-policy-binding "$SA" \
      --project="$PROJECT" --member="group:${OPERATORS}" \
      --role=roles/iam.serviceAccountTokenCreator >/dev/null

    # Converge on group-only: a person named here would be standing
    # authority that no group membership change could take away.
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
    echo ">>> act as it by joining $OPERATORS"
    echo ">>> next, as an organization admin: just gcp-bootstrap-org-roles"

# Grant the bootstrap identity its organisation roles (as an org admin).
gcp-bootstrap-org-roles org="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
    SA=$(just _gcp-bootstrap-sa)

    # folderCreator + folderIamAdmin rather than folderAdmin: together
    # they create a folder and grant roles inside it, and cannot delete
    # one. projectCreator is org-scoped because the folder it will
    # create does not exist yet.
    for role in \
      roles/resourcemanager.folderCreator \
      roles/resourcemanager.folderIamAdmin \
      roles/resourcemanager.projectCreator \
      roles/orgpolicy.policyAdmin ; do
      echo ">>> granting $role on organization $ORG"
      gcloud organizations add-iam-policy-binding "$ORG" \
        --member="serviceAccount:${SA}" --role="$role" \
        --condition=None >/dev/null
    done
    echo
    echo ">>> $SA can now create the folder"

# Show the bootstrap identity, its rights, and who may impersonate it.
gcp-bootstrap-status org="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
    PROJECT=$(just _gcp-bootstrap-project)
    SA=$(just _gcp-bootstrap-sa)
    echo "project   $PROJECT"
    echo "identity  $SA"
    # Captured before printing: piping into sed takes sed's exit status,
    # so a denial would print nothing and read as "none".
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

# =========================================================================
# Access groups
#
# Roles bind to groups, and membership is the only thing that moves
# afterwards. Nobody stands in the admins group: break-glass is a
# Workspace super admin adding a member, which the Admin audit log
# records -- unlike switching gcloud's active account, which nothing does.
# =========================================================================

# Bind the access groups' roles at the organisation (as a super admin).
gcp-groups-bind org="":
    #!/usr/bin/env zsh
    set -e
    # The groups themselves are made in the Admin console, not here.
    # Cloud Identity writes go through ADC and require a quota project,
    # and at foundation time no project exists to name. Group reads and
    # IAM bindings need none, so this verifies and binds.
    ORG=$(just _gcp-org-id "{{ org }}")
    DOMAIN=$(just _gcp-domain "{{ org }}")

    MISSING=0
    for spec in \
      "{{ ADMINS_GROUP }}:roles/resourcemanager.organizationAdmin" \
      "{{ OPERATORS_GROUP }}:roles/resourcemanager.organizationViewer" \
      "{{ BILLING_GROUP }}:" ; do
      NAME="${spec%%:*}"
      ROLE="${spec#*:}"
      EMAIL="${NAME}@${DOMAIN}"

      if [[ -z "$ROLE" ]]; then
        echo ">>> $EMAIL is bound on the billing account, not here"
        continue
      fi

      # No Cloud Identity lookup first: IAM validates the principal
      # itself and says so plainly, while every read of the groups API
      # wants a quota project there is no project to provide.
      echo ">>> binding $ROLE to $EMAIL on organization $ORG"
      if ! gcloud organizations add-iam-policy-binding "$ORG" \
             --member="group:${EMAIL}" --role="$ROLE" \
             --condition=None >/dev/null; then
        echo "    failed -- check the group exists with exactly that address" >&2
        MISSING=1
      fi
    done

    if [[ "$MISSING" -ne 0 ]]; then
      exit 1
    fi

    echo
    echo ">>> bound. Nobody should stand in {{ ADMINS_GROUP }}@${DOMAIN}"
    echo ">>> billing: as an administrator of the billing account, run"
    echo ">>>   gcloud billing accounts add-iam-policy-binding <account-id> \\"
    echo ">>>     --member=group:{{ BILLING_GROUP }}@${DOMAIN} --role=roles/billing.admin"

# Add a member to an access group (needs an active project you can use).
gcp-access-grant group member:
    #!/usr/bin/env zsh
    set -e
    DOMAIN=$(just _gcp-domain)
    gcloud identity groups memberships add \
      --group-email="{{ group }}@${DOMAIN}" --member-email="{{ member }}"

# Remove a member from an access group (needs an active project you can use).
gcp-access-revoke group member:
    #!/usr/bin/env zsh
    set -e
    DOMAIN=$(just _gcp-domain)
    gcloud identity groups memberships delete \
      --group-email="{{ group }}@${DOMAIN}" --member-email="{{ member }}"

# List who is in each access group.
gcp-access-status org="":
    #!/usr/bin/env zsh
    set -e
    DOMAIN=$(just _gcp-domain "{{ org }}")
    for NAME in {{ ADMINS_GROUP }} {{ OPERATORS_GROUP }} {{ BILLING_GROUP }} ; do
      EMAIL="${NAME}@${DOMAIN}"
      echo "$EMAIL"
      if OUT=$(gcloud identity groups memberships list \
                 --group-email="$EMAIL" \
                 --format='value(preferredMemberKey.id)' 2>/dev/null); then
        printf '%s\n' "${OUT:-  (empty)}" | sed 's/^/  /'
      else
        echo "  not readable by this account"
      fi
    done
