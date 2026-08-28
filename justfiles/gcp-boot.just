# The boot plane: the throwaway control plane that creates the folder
# and the management project, and the identity it runs as.
#
# Split out of gcp.just because these twelve are one procedure, written
# up in docs/recipes/infra/crossplane-bootstrap.md. Helpers and shared
# variables stay in gcp.just -- justfile imports share one namespace.

# Check the organisation, billing and rights held before bootstrapping.
gcp-boot-preflight org="":
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

    # Collected rather than exited on, so a failing run still reports
    # everything. What blocks is printed again at the end.
    BLOCKS=()

    BILLING=$(gcloud billing accounts list --filter='OPEN=True' \
                --format='value(ACCOUNT_ID)' 2>/dev/null | tr '\n' ' ' || true)
    if [[ -z "${BILLING// }" ]]; then
      echo "billing           NONE open"
      BLOCKS+=("no open billing account -- create one in the console, no API exists")
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
    # Not fatal, unlike the describe above. Browser carries no
    # organizations.getIamPolicy, so the operating user legitimately
    # cannot read this -- and a denial must never render as "(none)".
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

    # What the two paths need, stated rather than asserted, because the
    # seed identity is granted these later -- not the operator.
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
    echo "nothing blocking. next: just gcp-boot-seed"

# =========================================================================
# The seed identity
#
# Two recipes because two accounts are involved. The operator owns the
# billing account and can create projects, but only an organisation
# admin can grant roles at the organisation -- which is the whole of
# what break-glass is for.
#
# The seed project holding the identity carries no installation code:
# one serves the organisation. The identities inside it do carry one,
# because each creates a particular installation.
# =========================================================================

# Create the seed project and the seed identity in it.
gcp-boot-seed org="" billing="" yes="":
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

    # Same reason, and worse here: a swallowed denial reads as "none
    # exists" and mints a second seed project, consuming an id forever.
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
    DOMAIN=$(just _gcp-domain "{{ org }}")

    echo "organization    $ORG"
    echo "billing         $BILLING"
    echo "as              $ME"
    echo "seed project    $PROJECT ($PROJECT_ACT)"
    echo "identity        sa-{{ CODE }}-boot@${PROJECT}.iam.gserviceaccount.com"
    echo "impersonated by {{ PLATFORM_ADMIN_GROUP }}@${DOMAIN}"
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

    # As the caller, who owns this project by having created it. The
    # identity inside it holds organisation roles and nothing here, so
    # it cannot enable its own APIs -- the asymmetry that makes this the
    # only place these belong.
    #
    # cloudasset is for auditing the organisation rather than for
    # building anything: queries are billed to the project they run
    # from, which is the one the reading identity lives in.
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

    # A freshly created service account is not immediately visible to
    # IAM policy binding, and IAM reads it back through replicas that
    # do not all have it yet -- so this can fail on an account that was
    # just made and will succeed shortly after. No retry: every step
    # here is a no-op when it has already been done, so the answer is
    # to run the recipe again rather than to wait inside it.
    echo ">>> granting roles/billing.user on billing account $BILLING"
    if ! ERR=$(gcloud billing accounts add-iam-policy-binding "$BILLING" \
                 --member="serviceAccount:${SA}" --role=roles/billing.user \
                 2>&1 >/dev/null); then
      echo "could not grant billing.user to $SA on $BILLING:" >&2
      printf '%s\n' "$ERR" | sed 's/^/  /' >&2
      echo "if $SA was just created, run this again -- it is idempotent" >&2
      exit 1
    fi

    # The group, never a person. Membership is then the whole of who can
    # act as this identity, and removing it is one revocation in one
    # place rather than an edit to this policy. platform-admin rather
    # than platform-viewer: assuming this identity is a write capability,
    # so it is joined for a task and left.
    PLATFORM_ADMIN="{{ PLATFORM_ADMIN_GROUP }}@${DOMAIN}"
    echo ">>> letting $PLATFORM_ADMIN impersonate $SA"
    gcloud iam service-accounts add-iam-policy-binding "$SA" \
      --project="$PROJECT" --member="group:${PLATFORM_ADMIN}" \
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
    echo ">>> act as it by joining $PLATFORM_ADMIN, and leave after"
    echo ">>> if it holds no organization roles yet, an organization admin"
    echo ">>> runs: just gcp-boot-seed-grant-org-roles"

# Grant the seed identity the roles it needs at the organisation.
gcp-boot-seed-grant-org-roles org="" yes="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
    SA=$(just _gcp-boot-sa)
    ME=$(gcloud config get-value account)

    # folderCreator + folderIamAdmin rather than folderAdmin: together
    # they create a folder and grant roles inside it, and cannot delete
    # one. projectCreator is org-scoped because the folder it will
    # create does not exist yet.
    #
    # browser is read-only and not optional: GCP masks a project that
    # does not exist as permission denied unless the caller can read the
    # hierarchy, and an identity that cannot tell "absent" from
    # "forbidden" never gets past its first observation to create one.
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

# Close the seed identity: no impersonation, no org-wide creation.
gcp-boot-seed-close org="" yes="":
    #!/usr/bin/env zsh
    set -e
    # Two halves, and the first is the door: with no
    # serviceAccountTokenCreator nobody can become this identity at all,
    # whatever it still holds. The second removes what it holds that is
    # only needed to build -- the folder and the management project both
    # exist afterwards, and the plane creates instance projects and
    # binds folder IAM as the platform identity, which the composite
    # grants on the folder.
    #
    # orgpolicy.policyAdmin, cloudasset.viewer and browser stay: GCP
    # refuses orgpolicy at folder scope, so it is granted here or
    # nowhere, and gcp-org-setup and gcp-policy-status read and write
    # policy as this identity. Re-open with gcp-boot-seed-grant-org-roles
    # and gcp-boot-seed-open.
    ORG=$(just _gcp-org-id "{{ org }}")
    SA=$(just _gcp-boot-sa)
    PROJECT=$(just _gcp-seed-project)
    DOMAIN=$(just _gcp-domain "{{ org }}")
    PLATFORM_ADMIN="{{ PLATFORM_ADMIN_GROUP }}@${DOMAIN}"

    REVOKE=(
      roles/resourcemanager.folderCreator
      roles/resourcemanager.folderIamAdmin
      roles/resourcemanager.projectCreator
    )

    echo "organization    $ORG"
    echo "identity        $SA"
    echo "revoking impersonation by"
    echo "                group:$PLATFORM_ADMIN"
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
      --project="$PROJECT" --member="group:${PLATFORM_ADMIN}" \
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
gcp-boot-seed-open org="" yes="":
    #!/usr/bin/env zsh
    set -e
    SA=$(just _gcp-boot-sa)
    PROJECT=$(just _gcp-seed-project)
    DOMAIN=$(just _gcp-domain "{{ org }}")
    PLATFORM_ADMIN="{{ PLATFORM_ADMIN_GROUP }}@${DOMAIN}"

    echo "identity        $SA"
    echo "impersonated by group:$PLATFORM_ADMIN"
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
      --project="$PROJECT" --member="group:${PLATFORM_ADMIN}" \
      --role=roles/iam.serviceAccountTokenCreator >/dev/null
    echo ">>> $PLATFORM_ADMIN may impersonate $SA again"

# Show the seed identity, its rights, and who may impersonate it.
gcp-boot-seed-status org="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id "{{ org }}")
    PROJECT=$(just _gcp-seed-project)
    SA=$(just _gcp-boot-sa)
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

# Impersonate the seed identity, via ADC (opens a browser).
gcp-boot-seed-impersonate:
    #!/usr/bin/env zsh
    set -e
    SA=$(just _gcp-boot-sa)
    echo ">>> ADC will impersonate $SA"
    gcloud auth application-default login --impersonate-service-account="$SA"

# Revoke the impersonation. The credential is destroyed.
gcp-boot-seed-impersonate-revoke:
    @gcloud auth application-default revoke --quiet

# Show whether the seed identity is being impersonated.
gcp-boot-seed-impersonate-status:
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
    # Exercised, not described. A file that reads correctly still fails
    # once its source credential needs reauthentication, and that error
    # then surfaces inside a controller that cannot prompt for one.
    if ERR=$(gcloud auth application-default print-access-token 2>&1 >/dev/null); then
      echo "token        mints"
    else
      echo "token        FAILS -- run 'just gcp-boot-seed-impersonate'"
      printf '%s\n' "$ERR" | sed 's/^/  /'
      exit 1
    fi

# =========================================================================
# The throwaway control plane
#
# Exists for as long as it takes to create the folder and the management
# project, and is then discarded. The composite it applies is orphaned
# rather than deleted with it.
# =========================================================================

# Stand up the boot cluster: kind, Crossplane, the GCP providers.
gcp-boot-cluster-up:
    #!/usr/bin/env zsh
    set -e
    F="$HOME/.config/gcloud/application_default_credentials.json"
    if [[ ! -f "$F" ]]; then
      echo "no ADC -- run 'just gcp-boot-seed-impersonate' first" >&2
      exit 1
    fi
    if ! grep -q impersonated_service_account "$F"; then
      echo "ADC is not impersonating the seed identity --" >&2
      echo "run 'just gcp-boot-seed-impersonate'" >&2
      exit 1
    fi
    # Before kind and Helm, not after. A credential that needs
    # reauthentication fails only when the provider first calls out,
    # which is ten minutes later and reported as a folder that cannot
    # be observed.
    if ! ERR=$(gcloud auth application-default print-access-token 2>&1 >/dev/null); then
      echo "ADC cannot mint a token -- run 'just gcp-boot-seed-impersonate'" >&2
      printf '%s\n' "$ERR" | sed 's/^/  /' >&2
      exit 1
    fi

    kind get clusters | grep -q '^{{ BOOT_CLUSTER }}$' \
      || kind create cluster --name '{{ BOOT_CLUSTER }}'

    # Crossplane from the boot-management-plane chart, providers from
    # applies on the real plane. Both, because the chart templates
    # Provider and Function CRs in the same release as the subchart that
    # installs their CRDs, so Helm rejects the render on a new cluster --
    # its provider block is an audit re-export, not an install path.
    # Argo is off: a plane that lives for minutes reconciles nothing.
    # update rather than build: the Chart.lock is not committed, so
    # there is one pin rather than two that can disagree. The versions
    # in Chart.yaml are exact, so this resolves to them and nothing
    # else, and check-versions holds them to the composition.
    helm dependency update infra/helm/boot-management-plane >/dev/null

    # What was fetched, against what Chart.yaml pins. check-versions
    # holds Chart.yaml to the composition, so the two together are what
    # keep this cluster on the version the management plane runs.
    CHART=infra/helm/boot-management-plane
    for dep in crossplane argo-cd; do
      WANT=$(awk -v d="  - name: $dep" '$0 == d {f = 1; next}
                 f && /version:/ {gsub(/"/, "", $2); print $2; exit}' \
               "$CHART/Chart.yaml")
      if [[ ! -f "$CHART/charts/$dep-$WANT.tgz" ]]; then
        echo "helm did not resolve $dep $WANT -- fetched:" >&2
        ls "$CHART/charts/" >&2
        exit 1
      fi
    done
    helm upgrade --install boot-management-plane infra/helm/boot-management-plane \
      --kube-context {{ BOOT_CTX }} \
      --namespace crossplane-system --create-namespace \
      --set argo-cd.enabled=false --set providers.enabled=false --wait

    # Only what this plane needs: a folder, a project and its APIs
    # Narrowed to the providers whose kinds the composite composes,
    # which is what this plane has to reconcile before handing over: a
    # folder, a project and its APIs (cloudplatform), a network and a
    # GKE cluster (compute, container), helm to install onto that
    # cluster, and secretmanager for the container the plane's own
    # credential is stored in. dns, sql, storage and certificatemanager
    # compose nothing yet and belong to the management plane rather
    # than to a cluster that lives for minutes.
    #
    # The rule is what the composite composes, not what an instance
    # eventually needs -- a composed kind this plane cannot store fails
    # the whole pipeline, not just its own resource. Versions come from
    # the manifest Argo applies, so only the set is narrowed.
    MANIFEST=infra/platform/crossplane-providers/providers.yml
    for name in provider-gcp-cloudplatform provider-gcp-compute \
                provider-gcp-container provider-gcp-secretmanager \
                provider-helm function-patch-and-transform \
                function-go-templating function-auto-ready \
                function-environment-configs ; do
      PKG=$(grep -A2 "name: $name\$" "$MANIFEST" | grep 'package:' | awk '{print $2}')
      if [[ -z "$PKG" ]]; then
        echo "no package for $name in $MANIFEST" >&2
        exit 1
      fi
      KIND=Provider
      case "$name" in function-*) KIND=Function ;; esac
      echo ">>> installing $PKG"
      kubectl --context {{ BOOT_CTX }} apply -f - <<YAML
    apiVersion: pkg.crossplane.io/v1
    kind: $KIND
    metadata:
      name: $name
    spec:
      package: $PKG
    YAML
    done

    echo ">>> waiting for the provider and function"
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      provider.pkg.crossplane.io/provider-gcp-cloudplatform
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      provider.pkg.crossplane.io/provider-gcp-container
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      provider.pkg.crossplane.io/provider-gcp-secretmanager
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      provider.pkg.crossplane.io/provider-helm
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      function.pkg.crossplane.io/function-patch-and-transform
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      function.pkg.crossplane.io/function-go-templating
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      function.pkg.crossplane.io/function-auto-ready
    kubectl --context {{ BOOT_CTX }} wait --for=condition=healthy --timeout=600s \
      function.pkg.crossplane.io/function-environment-configs

    kubectl --context {{ BOOT_CTX }} create secret generic gcp-creds \
      -n crossplane-system --from-file=creds="$F" \
      --dry-run=client -o yaml | kubectl --context {{ BOOT_CTX }} apply -f -

    # projectID is required by the provider config and unused by a
    # Folder, which is parented by an organisation rather than a project.
    PROJECT=$(just _gcp-seed-project)
    kubectl --context {{ BOOT_CTX }} apply -f - <<YAML
    apiVersion: gcp.m.upbound.io/v1beta1
    kind: ClusterProviderConfig
    metadata:
      name: default
    spec:
      projectID: "$PROJECT"
      credentials:
        source: Secret
        secretRef:
          namespace: crossplane-system
          name: gcp-creds
          key: creds
    YAML

    # How the boot plane installs onto the management cluster. Two
    # halves, because a GKE kubeconfig carries no usable credential:
    # `credentials` is the connection Secret the composed Cluster
    # writes, holding the endpoint and the CA, and `identity` is the
    # same impersonated ADC the GCP providers use, which is what mints
    # the token. So the plane holds no key for the cluster either.
    #
    # Applied before the Secret exists, which is harmless: nothing reads
    # this config until a Release names it, and by then gcp-boot-mgmt-apply
    # has reconciled the cluster.
    kubectl --context {{ BOOT_CTX }} apply -f - <<YAML
    apiVersion: helm.m.crossplane.io/v1beta1
    kind: ClusterProviderConfig
    metadata:
      name: {{ CODE }}-mgmt
    spec:
      credentials:
        source: Secret
        secretRef:
          namespace: crossplane-system
          name: gke-{{ CODE }}-c-mgmt-kubeconfig
          key: kubeconfig
      identity:
        type: GoogleApplicationCredentials
        source: Secret
        secretRef:
          namespace: crossplane-system
          name: gcp-creds
          key: creds
    YAML

    kubectl --context {{ BOOT_CTX }} apply \
      -f infra/platform/crossplane-xrds/xmanagementplane-xrd.yml
    kubectl --context {{ BOOT_CTX }} wait --for=condition=established --timeout=120s \
      xrd/xmanagementplanes.platform.repldriven.com
    kubectl --context {{ BOOT_CTX }} apply \
      -f infra/platform/crossplane-xrds/xmanagementplane-composition.yml

    echo
    echo ">>> plane ready. Next: just gcp-boot-mgmt-apply <parent> [display-name]"

# Print the installation manifest, resolved. Takes the folder's parent.
gcp-boot-mgmt-manifest parent name="" folder="" mgmt="" billing="" recovery="":
    #!/usr/bin/env zsh
    set -e
    # stdout carries the manifest and nothing else, so this can be
    # redirected straight into a file; every message below goes to
    # stderr. That file is the installation's whole record -- see
    # docs/plan/cloud-just-migration.md -- and it lives in a private
    # repository, because two of the values here are identifiers worth
    # keeping out of a public one.
    #
    # Anything generated rather than derived is minted per call, so
    # capture the output rather than re-running this and expecting the
    # same answer.
    # Deterministic, because the code is what tells one installation
    # from another -- a suffix on top of it would only obscure that.
    # Pass a name where the folder is handed to you already named.
    NAME="{{ name }}"
    if [[ -z "$NAME" ]]; then
      NAME="fldr-{{ CODE }}"
      echo ">>> naming the folder $NAME" >&2
    fi
    # Refuse to make a second installation by accident. GCP allows the
    # duplicate name, so this is the only thing that would notice -- and
    # with a deterministic name it is what catches a rebuild that meant
    # to adopt.
    if [[ -z "{{ folder }}" ]]; then
      case "{{ parent }}" in
        organizations/*) SCOPE="--organization=${${:-{{ parent }}}#organizations/}" ;;
        folders/*)       SCOPE="--folder=${${:-{{ parent }}}#folders/}" ;;
        *) echo "parent must be organizations/<id> or folders/<id>" >&2; exit 1 ;;
      esac
      EXISTING=$(gcloud resource-manager folders list $SCOPE \
                   --filter="displayName=$NAME" \
                   --format='value(name)' 2>/dev/null | head -1 || true)
      if [[ -n "$EXISTING" ]]; then
        echo "a folder named $NAME already exists under {{ parent }}: folders/$EXISTING" >&2
        echo "adopt it:  just gcp-boot-mgmt-apply {{ parent }} $NAME folders/$EXISTING" >&2
        echo "or choose a different name" >&2
        exit 1
      fi
    fi
    # Omitted unless passed, and normally not passed. The account is a
    # property of the identity -- it holds billing.user on exactly one --
    # so the manifest does not record it. Once a project exists the
    # provider's late-initialisation owns forProvider.billingAccount, so
    # a manifest without this leaves billing alone rather than unsetting
    # it. gcp-boot-mgmt-apply supplies it, because creating a project is the
    # one moment it is needed.
    BILLING_LINE=""
    if [[ -n "{{ billing }}" ]]; then
      BILLING_LINE="billingAccountId: \"{{ billing }}\""
    fi

    # A project id is consumed permanently and cannot be undeleted into
    # usefulness, so it is generated once and then carried in the
    # manifest. That is what stops a rebuilt plane minting a second one.
    MGMT="{{ mgmt }}"
    if [[ -z "$MGMT" ]]; then
      MGMT="prj-{{ CODE }}-c-mgmt-$(openssl rand -hex 3)"
      echo ">>> naming the management project $MGMT" >&2
    fi

    # Minted here rather than added when backups are first wanted, so a
    # from-scratch installation has somewhere to keep what it would be
    # rebuilt from without anyone deciding to. Its own id for the same
    # reason the management project's is minted once: consumed
    # permanently, and not derivable afterwards.
    # Omitted unless an id is given, which is what a bootstrap wants:
    # the boot cluster composes as little as it can, and the plane
    # composes the recovery project once it is reconciling. An id here
    # adopts or creates one at bootstrap instead.
    RECOVERY_BLOCK=""
    if [[ -n "{{ recovery }}" ]]; then
      RECOVERY_BLOCK=$(printf '  recovery:\n    projectId: "%s"' "{{ recovery }}")
    fi

    # Set only when the project already exists, for the same reason the
    # folder's adoption field is: a pre-set external name turns a
    # first-run create into a failed observe.
    MGMT_ADOPT=""
    if [[ -n "{{ mgmt }}" ]]; then
      # Prefixed: that is the form Crossplane records as the external
      # name, and adoption matches on it exactly.
      case "$MGMT" in
        projects/*) MGMT_ADOPT="adopt: \"$MGMT\""; MGMT="${MGMT#projects/}" ;;
        *)          MGMT_ADOPT="adopt: \"projects/$MGMT\"" ;;
      esac
    fi

    # Resolved from the parent where that names an organisation, rather
    # than rediscovered. An argument the caller supplied and the recipe
    # then ignores is a run that fails on a question already answered --
    # and discovery fails outright where more than one organisation is
    # visible, or where a token has expired.
    PARENT="{{ parent }}"
    ORG_ARG=""
    if [[ "$PARENT" == organizations/* ]]; then
      ORG_ARG="${PARENT#organizations/}"
    fi
    DOMAIN=$(just _gcp-domain "$ORG_ARG")

    FOLDER="{{ folder }}"
    if [[ -n "$FOLDER" && "$FOLDER" != folders/* ]]; then
      FOLDER="folders/$FOLDER"
    fi
    ADOPT=""
    if [[ -n "$FOLDER" ]]; then
      ADOPT="folderId: \"$FOLDER\""
    fi

    # region, zone and regionCode are stated rather than left to the
    # XRD's defaults. They are load-bearing -- a name carries the
    # region abbreviation, and moving either rebuilds the subnet and
    # the cluster -- so a default that changed underneath a live
    # installation would move it silently.
    # The document lives in infra/platform/installation.yml.tmpl so it
    # can be read and reviewed as YAML rather than as shell.
    CODE="{{ CODE }}" REGION="{{ REGION }}" REGION_CODE="{{ REGION_CODE }}" \
    ZONE="{{ ZONE }}" BILLING_LINE="$BILLING_LINE" DOMAIN="$DOMAIN" \
    PLATFORM_VIEWER_GROUP="{{ PLATFORM_VIEWER_GROUP }}" \
    PLATFORM_ADMIN_GROUP="{{ PLATFORM_ADMIN_GROUP }}" \
    CLUSTER_ADMIN_GROUP="{{ CLUSTER_ADMIN_GROUP }}" \
    SECRETS_ADMIN_GROUP="{{ SECRETS_ADMIN_GROUP }}" \
    RECOVERY_BLOCK="$RECOVERY_BLOCK" MGMT="$MGMT" MGMT_ADOPT="$MGMT_ADOPT" \
    SOURCE_REPO="{{ SOURCE_REPO }}" SOURCE_REVISION="{{ SOURCE_REVISION }}" \
    SOURCE_PATH_PREFIX="{{ SOURCE_PATH_PREFIX }}" \
    MANIFEST_REPO_URL="{{ MANIFEST_REPO_URL }}" \
    PARENT="{{ parent }}" NAME="$NAME" ADOPT="$ADOPT" \
    python3 -c 'import os,sys,string; d=string.Template(open(sys.argv[1]).read()).substitute(os.environ); sys.stdout.write("\n".join(l for l in d.split("\n") if l.strip()) + "\n")' \
      infra/platform/installation.yml.tmpl

# Apply the committed manifest to the boot cluster, as its bootstrap.
gcp-boot-mgmt-apply billing="" yes="":
    #!/usr/bin/env zsh
    set -e
    # The committed manifest, not a composite assembled here. It carries
    # the adopt values, so applying it twice adopts rather than building
    # a second installation -- and it is the same document Argo
    # reconciles from, so the two planes cannot disagree about what the
    # installation is.
    FILE={{ INSTALLATIONS_REPO }}/{{ CODE }}/installation.yml
    if [[ ! -f "$FILE" ]]; then
      echo "no manifest at $FILE" >&2
      echo "a first installation is rendered, then committed, then" >&2
      echo "applied -- the ids it holds are consumed permanently:" >&2
      echo "  just gcp-boot-mgmt-manifest organizations/<org-id> > $FILE" >&2
      exit 1
    fi

    # Supplied here and not in the committed manifest, because creating
    # a project is the one moment it is needed. Discovered from the
    # identity's own binding, so applying does not depend on remembering
    # it, and the file never carries it.
    BILLING="{{ billing }}"
    if [[ -z "$BILLING" ]]; then
      BILLING=$(just _gcp-billing-account)
    fi

    # Merged client-side rather than templated into the file: both are
    # true of this act of creation rather than of the installation, and
    # the file has to stay the thing Argo reads. --local needs no
    # cluster and no yq.
    #
    # bootstrap because this recipe is the boot plane, and the boot
    # plane is the only thing that may install the management plane onto
    # its own cluster.
    DOC=$(kubectl patch --local -f "$FILE" --type merge -o yaml \
      -p "{\"spec\":{\"billingAccountId\":\"$BILLING\",\"management\":{\"bootstrap\":true}}}")

    # Shown whole before anything is applied: this is the document the
    # boot cluster receives, and the ids in it are consumed permanently.
    echo "applying    $FILE"
    echo "to          {{ BOOT_CTX }}"
    echo "billing     $BILLING"
    echo
    print -r -- "$DOC" | sed 's/^/  /'
    echo
    echo "management.bootstrap is true here, so this cluster will create"
    echo "and update the Crossplane and Argo releases on the management"
    echo "cluster. Never against an installation that already has one."
    echo
    if [[ -z "{{ yes }}" ]]; then
      printf 'proceed? [y/N] '
      read -r REPLY || REPLY=""
      if [[ "$REPLY" != [yY]* ]]; then
        echo "abandoned"
        exit 1
      fi
    fi

    print -r -- "$DOC" | kubectl --context {{ BOOT_CTX }} apply -f -

    echo ">>> applied $FILE"
    echo ">>> waiting for the folder and management project"
    kubectl --context {{ BOOT_CTX }} -n crossplane-system wait \
      --for=condition=ready --timeout=300s \
      xmanagementplane/{{ CODE }} || true
    just gcp-boot-mgmt-status

# Report the composite and the folder it made.
gcp-boot-mgmt-status:
    #!/usr/bin/env zsh
    set -e
    kubectl --context {{ BOOT_CTX }} -n crossplane-system get \
      xmanagementplane {{ CODE }} -o yaml 2>/dev/null \
      | grep -E 'folderId|folderName|type: (Ready|Synced)|status: "(True|False)"|reason:' \
      || echo "no composite applied yet"
    echo
    kubectl --context {{ BOOT_CTX }} -n crossplane-system \
      get folder.cloudplatform.gcp.m.upbound.io 2>/dev/null || true

# Delete the boot cluster. The folder is orphaned, not deleted.
gcp-boot-cluster-down:
    @kind delete cluster --name {{ BOOT_CLUSTER }}

# =========================================================================
# The management cluster
#
# The durable plane, inside the installation's folder -- as opposed to
# the throwaway one above, which exists only long enough to create it.
# =========================================================================
