# =========================================================================
# Internal helpers
# =========================================================================

# Print this environment's GCP project id on stdout; exit 1 if unknown.
# Use from other recipes as: `PROJECT_ID=$(just _gcp-project-id)`.
[private]
_gcp-project-id:
    #!/usr/bin/env zsh
    set -e
    # `pass` first: it records the project for THIS environment, where
    # `gcloud config` records whatever project was last selected on this
    # machine. Those differ the moment you touch a second environment,
    # and the gcloud answer is the one that silently provisions into the
    # wrong project.
    PROJECT_ID=$(pass show {{ PASS_ENV }}/ids/project 2>/dev/null | head -1)
    if [[ -z "$PROJECT_ID" ]]; then
      PROJECT_ID=$(gcloud config get project 2>/dev/null)
    fi
    if [[ -z "$PROJECT_ID" ]]; then
      echo "no project for {{ QUEENSWOOD_ENV }}: set {{ PASS_ENV }}/ids/project in pass, or run 'just gcp-project-create'" >&2
      exit 1
    fi
    printf '%s\n' "$PROJECT_ID"

# Fail unless the logged-in gcloud account matches this environment's.
[private]
_gcp-assert-account:
    #!/usr/bin/env zsh
    set -e
    # Skipped silently when the entry is absent, so a store that hasn't
    # been populated yet doesn't block every cloud recipe.
    WANT=$(pass show {{ PASS_ENV }}/ids/account 2>/dev/null | head -1)
    if [[ -z "$WANT" ]]; then
      exit 0
    fi
    HAVE=$(gcloud config get-value account 2>/dev/null)
    if [[ "$WANT" != "$HAVE" ]]; then
      echo "gcloud is authenticated as $HAVE, but {{ QUEENSWOOD_ENV }} belongs to $WANT" >&2
      echo "run: gcloud config set account $WANT" >&2
      exit 1
    fi

# Print the kubeconfig context name for the queenswood GKE cluster.
# Use as: `KCTX=$(just _gke-ctx)`.
[private]
_gke-ctx:
    @printf 'gke_%s_{{ ZONE }}_queenswood-gke\n' "$(just _gcp-project-id)"

# Print a GCP service-account email for the given account-id.
# Use as: `EMAIL=$(just _gcp-sa-email crossplane-provider)`.
[private]
_gcp-sa-email name:
    @printf '%s@%s.iam.gserviceaccount.com\n' '{{ name }}' "$(just _gcp-project-id)"

# =========================================================================
# Org-level (one-time)
# =========================================================================

# Grant org-level orgpolicy.policyAdmin to the operator + enforce skipDefaultNetworkCreation.
gcp-org-create:
    #!/usr/bin/env zsh
    set -e
    # roles/owner does NOT include orgpolicy perms; bind explicitly.
    : "${GCLOUD_ORGANIZATION_ID:?must be set}"
    ME=$(gcloud config get-value account)
    echo ">>> granting orgpolicy.policyAdmin to $ME on org $GCLOUD_ORGANIZATION_ID"
    gcloud organizations add-iam-policy-binding "$GCLOUD_ORGANIZATION_ID" \
      --member="user:${ME}" --role="roles/orgpolicy.policyAdmin" --condition=None
    # Child projects inherit this so per-project policy work is unnecessary.
    echo ">>> setting compute.skipDefaultNetworkCreation on org"
    printf 'name: organizations/%s/policies/compute.skipDefaultNetworkCreation\nspec:\n  rules:\n    - enforce: true\n' "$GCLOUD_ORGANIZATION_ID" \
      | gcloud org-policies set-policy /dev/stdin

# =========================================================================
# Project-level
# =========================================================================

# Create a new GCP project, link billing, return the ID on stdout.
gcp-project-create suffix=`openssl rand -hex 3`:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID="queenswood-{{ suffix }}"
    org_flag=""
    if [[ -n "${GCLOUD_ORGANIZATION_ID:-}" ]]; then
      org_flag="--organization=${GCLOUD_ORGANIZATION_ID}"
      echo ">>> creating project $PROJECT_ID under org $GCLOUD_ORGANIZATION_ID" >&2
    else
      echo ">>> creating project $PROJECT_ID (no org)" >&2
    fi
    gcloud projects create "$PROJECT_ID" --name=queenswood $org_flag >&2
    : "${GCP_BILLING_ACCOUNT_ID:=$(gcloud beta billing accounts list \
        --filter='OPEN=True' --format='value(name)' | head -1)}"
    if [[ -z "$GCP_BILLING_ACCOUNT_ID" ]]; then
      echo "no open billing account; set GCP_BILLING_ACCOUNT_ID" >&2
      exit 1
    fi
    echo ">>> linking billing $GCP_BILLING_ACCOUNT_ID" >&2
    gcloud beta billing projects link "$PROJECT_ID" \
      --billing-account="$GCP_BILLING_ACCOUNT_ID" >&2
    gcloud config set project "$PROJECT_ID" >&2
    printf '%s\n' "$PROJECT_ID"

# Full retirement: gcp-down + delete the GCP project.
gcp-project-delete:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(just _gcp-project-id)
    just gcp-down

    # DNS zone left behind (observe-only on the Crossplane side). dnsName
    # claim releases when project hard-deletes after the 30-day window.
    echo ">>> deleting GCP project $PROJECT_ID"
    gcloud projects delete "$PROJECT_ID" --quiet
    gcloud config unset project >/dev/null 2>&1 || true
    echo ">>> recover within 30 days: gcloud projects undelete $PROJECT_ID"

# =========================================================================
# Auth
# =========================================================================

# Authenticate gcloud as the operator (you).
gcp-user-login:
    gcloud auth login

# Set ADC to impersonate the crossplane-provider SA (no key file).
gcp-crossplane-login:
    gcloud auth application-default login \
      --impersonate-service-account=$(just _gcp-sa-email crossplane-provider)

# =========================================================================
# Bootstrap-of-bootstrap (one-time per project)
# =========================================================================

# Create the crossplane-provider SA + minimum IAM bindings Crossplane needs.
gcp-iam-bootstrap:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(just _gcp-project-id)
    SA=crossplane-provider
    SA_EMAIL=$(just _gcp-sa-email $SA)
    ME=$(gcloud config get-value account)

    gcloud config set project $PROJECT_ID

    # Bootstrap APIs only -- chicken-and-egg ones Crossplane needs to
    # enable other APIs via infra/gcp/services/services.yml.
    gcloud services enable \
      cloudresourcemanager.googleapis.com \
      iam.googleapis.com \
      iamcredentials.googleapis.com \
      serviceusage.googleapis.com

    # SA create idempotent: re-running is a no-op if the SA exists.
    if ! gcloud iam service-accounts describe $SA_EMAIL >/dev/null 2>&1; then
      gcloud iam service-accounts create $SA \
        --display-name "Crossplane Provider"
    fi

    # The two roles Crossplane needs to manage everything else; the
    # broader role list reconciles via the `gcp-roles` Argo app.
    for role in \
      roles/resourcemanager.projectIamAdmin \
      roles/serviceusage.serviceUsageAdmin ; do
      gcloud projects add-iam-policy-binding $PROJECT_ID \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="$role"
    done

    # Let the operator impersonate the SA (replaces key creation).
    gcloud iam service-accounts add-iam-policy-binding $SA_EMAIL \
      --member="user:${ME}" \
      --role="roles/iam.serviceAccountTokenCreator"

# Pre-create the DNS zone as the verified domain owner.
gcp-dns-zone-create:
    #!/usr/bin/env zsh
    set -e
    # Cloud DNS rejects SA-initiated zone creates -- the crossplane SA
    # isn't a verified domain owner. If the operator isn't verified
    # either, the create surfaces an ownership error and the recipe
    # launches `gcloud domains verify`.
    PROJECT_ID=$(just _gcp-project-id)
    DNS={{ QUEENSWOOD_DOMAIN }}.
    NAME={{ QUEENSWOOD_DNS_ZONE }}
    if gcloud dns managed-zones describe "$NAME" \
         --project="$PROJECT_ID" >/dev/null 2>&1; then
      echo ">>> $NAME already exists; skipping create"
      exit 0
    fi
    if ! gcloud dns managed-zones create "$NAME" \
           --dns-name="$DNS" --description="Public DNS for queenswood" \
           --visibility=public --project="$PROJECT_ID" 2>/tmp/dns-err; then
      if grep -q verifyManagedZoneDnsNameOwnership /tmp/dns-err; then
        PARENT="${DNS%.}"; PARENT="${PARENT#*.}"
        echo ">>> $PARENT not verified for $(gcloud config get-value account); opening Search Console"
        gcloud domains verify "$PARENT"
        echo ">>> after verifying, rerun: just gcp-dns-zone-create"
        exit 1
      fi
      cat /tmp/dns-err; exit 1
    fi

# =========================================================================
# Lifecycle (up / down)
# =========================================================================

# Cold start: existing GCP project + no kind cluster -> green stack.
gcp-up:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(just _gcp-project-id)
    just gcp-user-login
    just _gcp-assert-account
    just gcp-iam-bootstrap
    just gcp-crossplane-login
    just gcp-dns-zone-create
    just kind-xp-up
    just gcp-cloudsql-wire
    just gcp-health-check

# Tear down kind + GKE + expensive GCP resources; keep project, SA, DNS zone.
gcp-down:
    #!/usr/bin/env zsh
    set -e
    GKE_CTX=$(just _gke-ctx)

    # Drain GKE workloads BEFORE killing kind. GKE's CSI driver only
    # DeleteDisks PVs while the cluster is alive; destroying GKE with
    # PVCs around leaks PDs (~180 GiB/cycle from pulsar + fdb).
    # Crossplane (kind) must still be up to clear namespaced finalizers.
    if kubectl --context "$GKE_CTX" get ns queenswood-test >/dev/null 2>&1; then
      echo ">>> draining queenswood-test (lets CSI free PVs cleanly)"
      kubectl --context "$GKE_CTX" delete ns queenswood-test \
        --wait=true --timeout=5m \
        || echo "  (drain timed out -- some PDs may leak)" >&2
    fi

    just kind-xp-down || true
    just gcp-gke-delete
    just gcp-cloudsql-delete
    just gcp-ip-delete
    just gcp-cert-delete
    just gcp-vpc-delete
    just gcp-dns-records-delete

    kubectl config delete-context "$GKE_CTX" 2>/dev/null || true
    kubectl config delete-cluster "$GKE_CTX" 2>/dev/null || true
    kubectl config delete-user    "$GKE_CTX" 2>/dev/null || true

    echo ">>> kept: project, SA, IAM, DNS zone, registrar NS. Run 'just gcp-up' to come back."

# Delete the queenswood-gke GKE cluster.
gcp-gke-delete:
    #!/usr/bin/env zsh
    PROJECT_ID=$(just _gcp-project-id)
    echo ">>> deleting GKE cluster (most of the bill)"
    gcloud container clusters delete queenswood-gke \
      --zone={{ ZONE }} --project="$PROJECT_ID" --quiet 2>/dev/null || true

# Delete the queenswood-keycloak CloudSQL instance (cascades to database + user).
gcp-cloudsql-delete:
    #!/usr/bin/env zsh
    # Crossplane would normally cascade-delete but kind-xp-down kills it
    # first. Deleting the instance drops the keycloak Database + User
    # inside it (SQL admin treats them as sub-resources).
    PROJECT_ID=$(just _gcp-project-id)
    echo ">>> deleting CloudSQL databases (instance + database + user)"
    gcloud sql instances delete queenswood-keycloak \
      --project="$PROJECT_ID" --quiet 2>/dev/null || true

# Delete the queenswood-ingress-ip static IP.
gcp-ip-delete:
    #!/usr/bin/env zsh
    PROJECT_ID=$(just _gcp-project-id)
    echo ">>> deleting ingress IP"
    gcloud compute addresses delete queenswood-ingress-ip \
      --region={{ REGION }} --project="$PROJECT_ID" --quiet 2>/dev/null || true

# Delete the queenswood-cert Certificate + queenswood-dns-auth DNSAuthorization.
gcp-cert-delete:
    #!/usr/bin/env zsh
    PROJECT_ID=$(just _gcp-project-id)
    echo ">>> deleting Certificate + DNSAuthorization"
    gcloud certificate-manager certificates delete queenswood-cert \
      --location={{ REGION }} --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud certificate-manager dns-authorizations delete queenswood-dns-auth \
      --location={{ REGION }} --project="$PROJECT_ID" --quiet 2>/dev/null || true

# Delete the queenswood VPC + its subnets.
gcp-vpc-delete:
    #!/usr/bin/env zsh
    PROJECT_ID=$(just _gcp-project-id)
    echo ">>> deleting subnets + VPC"
    gcloud compute networks subnets delete queenswood-subnet \
      --region={{ REGION }} --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud compute networks subnets delete queenswood-proxy-only \
      --region={{ REGION }} --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud compute networks delete queenswood-vpc \
      --project="$PROJECT_ID" --quiet 2>/dev/null || true

# DESTRUCTIVE: delete the ManagedZone and all its records. Emergency use only.
gcp-dns-zone-delete:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(just _gcp-project-id)
    cat >&2 <<EOF
    ================================================================
    About to delete DNS ManagedZone '{{ QUEENSWOOD_DNS_ZONE }}'.

    Consequences:
      - All non-SOA/NS records will be deleted first (zone delete
        refuses while any user records remain).
      - On the next 'gcp-dns-zone-create', the new zone gets a
        DIFFERENT set of nameservers. The registrar delegation for
        {{ QUEENSWOOD_DOMAIN }} must be updated to match -- until then,
        public DNS resolution is broken.
      - GCP's domain-ownership verification for {{ QUEENSWOOD_DOMAIN }} may
        need redoing on the next create.
      - Each fresh zone burns one of GCP's per-domain NS-pool
        allocations; repeated delete/create can hit the quota.

    Use 'gcp-down' for routine teardown -- it keeps the zone.
    ================================================================
    EOF
    printf "Type 'yes' to proceed: "
    read confirm
    [[ "$confirm" == "yes" ]] || { echo "aborted." >&2; exit 1; }
    echo ">>> emptying record-sets in {{ QUEENSWOOD_DNS_ZONE }}"
    gcloud dns record-sets list --zone={{ QUEENSWOOD_DNS_ZONE }} --project="$PROJECT_ID" \
        --format='value(name,type)' 2>/dev/null \
      | while read name type; do
          [[ "$type" == "SOA" || "$type" == "NS" ]] && continue
          gcloud dns record-sets delete "$name" --type="$type" \
            --zone={{ QUEENSWOOD_DNS_ZONE }} --project="$PROJECT_ID" --quiet 2>/dev/null || true
        done
    echo ">>> deleting ManagedZone {{ QUEENSWOOD_DNS_ZONE }}"
    gcloud dns managed-zones delete {{ QUEENSWOOD_DNS_ZONE }} \
      --project="$PROJECT_ID" --quiet

# Delete the apex A + cert-validation CNAME records (keep the zone).
gcp-dns-records-delete:
    #!/usr/bin/env zsh
    PROJECT_ID=$(just _gcp-project-id)
    echo ">>> deleting apex A + cert CNAME (keeping zone)"
    gcloud dns record-sets delete {{ QUEENSWOOD_DOMAIN }}. --type=A --zone={{ QUEENSWOOD_DNS_ZONE }} \
      --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud dns record-sets list --zone={{ QUEENSWOOD_DNS_ZONE }} --project="$PROJECT_ID" \
        --filter="type=CNAME" --format='value(name)' 2>/dev/null \
      | while read n; do
          gcloud dns record-sets delete "$n" --type=CNAME --zone={{ QUEENSWOOD_DNS_ZONE }} \
            --project="$PROJECT_ID" --quiet 2>/dev/null || true
        done

# Block on each Argo Application reaching Healthy (helper for gcp-up).
kind-xp-wait-ready timeout="2700":
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(just _gcp-project-id)
    # Surfaces the ManagedZone's nameservers as soon as the zone is
    # queryable so the operator can paste them into the registrar --
    # cert validation gates on NS propagation.
    for app in crossplane-providers crossplane-configs crossplane-xrds \
               queenswood-gcp queenswood-platform; do
      echo ">>> $app"
      until kubectl --context {{ KIND_XP_CLUSTER }} -n argocd \
            get application/$app >/dev/null 2>&1; do sleep 5; done
      if [[ "$app" == "queenswood-platform" ]]; then
        until kubectl --context {{ KIND_XP_CLUSTER }} -n {{ QUEENSWOOD_ENV }} get \
              managedzone.dns.gcp.m.upbound.io {{ QUEENSWOOD_DNS_ZONE }} >/dev/null 2>&1; do
          sleep 5
        done
        until [[ -n "$(kubectl --context {{ KIND_XP_CLUSTER }} -n {{ QUEENSWOOD_ENV }} get \
              managedzone.dns.gcp.m.upbound.io {{ QUEENSWOOD_DNS_ZONE }} \
              -o jsonpath='{.status.atProvider.nameServers}' 2>/dev/null)" ]]; do
          sleep 5
        done
        echo
        echo "------------------------------------------------------"
        echo "ACTION: paste these 4 nameservers into your registrar's"
        echo "NS records for {{ QUEENSWOOD_DOMAIN }} (trailing dots"
        echo "optional). cert validation gates on this propagating."
        kubectl --context {{ KIND_XP_CLUSTER }} -n {{ QUEENSWOOD_ENV }} \
          get managedzone.dns.gcp.m.upbound.io {{ QUEENSWOOD_DNS_ZONE }} \
          -o jsonpath='{.status.atProvider.nameServers}' \
          | tr ',' '\n' | tr -d '[]"' | sed 's/^ */  /'
        echo "------------------------------------------------------"
        echo
      fi
      kubectl --context {{ KIND_XP_CLUSTER }} -n argocd wait \
        --for=jsonpath='{.status.health.status}'=Healthy \
        application/$app --timeout={{ timeout }}s
      if [[ "$app" == "queenswood-gcp" ]]; then
        gcloud container clusters get-credentials queenswood-gke \
          --zone={{ ZONE }} --project="$PROJECT_ID"
      fi
    done

# =========================================================================
# Per-deploy
# =========================================================================

# Wire the CloudSQL connection + SA email from Crossplane's status into platform values.
gcp-cloudsql-wire:
    #!/usr/bin/env zsh
    set -e
    # CloudSQL provisioning takes 5-10 minutes; wait timeout is 15m.
    PROJECT_ID=$(just _gcp-project-id)
    VALUES=infra/helm/queenswood-platform/values.yaml
    # Matches the cloud-sql-proxy GCP SA composed by the XPlatform XR.
    SA_EMAIL=$(just _gcp-sa-email keycloak-sql-proxy)

    echo "Waiting for XPlatform/queenswood status.connectionName (up to 15m)..."
    CONN=""
    deadline=$(( $(date +%s) + 900 ))
    while [[ -z "$CONN" && $(date +%s) -lt $deadline ]]; do
      CONN=$(kubectl --context {{ KIND_XP_CLUSTER }} -n crossplane-system \
               get xplatform.platform.queenswood.repldriven.com queenswood \
               -o jsonpath='{.status.connectionName}' 2>/dev/null || true)
      [[ -z "$CONN" ]] && sleep 10
    done
    if [[ -z "$CONN" ]]; then
      echo "" >&2
      echo "Timed out waiting for status.connectionName on XPlatform/queenswood." >&2
      echo "--- XPlatform conditions ---" >&2
      kubectl --context {{ KIND_XP_CLUSTER }} -n crossplane-system \
        get xplatform.platform.queenswood.repldriven.com queenswood \
        -o jsonpath='{range .status.conditions[*]}{.type}={.status}  reason={.reason}  message={.message}{"\n"}{end}' >&2 || true
      exit 1
    fi
    echo "Resolved connectionName: $CONN"
    echo "Pinning gcpServiceAccount:  $SA_EMAIL"

    cur_conn=$(yq -r '.queenswoodKeycloak.proxy.connectionName' $VALUES)
    cur_sa=$(yq -r '.queenswoodKeycloak.proxy.gcpServiceAccount' $VALUES)
    if [[ "$cur_conn" == "$CONN" && "$cur_sa" == "$SA_EMAIL" ]]; then
      echo "$VALUES already pinned; skipping commit."
      exit 0
    fi

    # sed not `yq -y -i` -- kislyuk/yq round-trips through JSON and
    # strips YAML comments. Two keys, unique paths, anchored sed safe.
    sed -i.bak -E "s|^([[:space:]]*connectionName:).*|\1 $CONN|" $VALUES
    sed -i.bak -E "s|^([[:space:]]*gcpServiceAccount:).*|\1 $SA_EMAIL|" $VALUES
    rm -f "${VALUES}.bak"

    git add $VALUES
    git commit -m "queenswood-platform: pin queenswood-keycloak CloudSQL connection + WI SA"
    git push
    echo "Pushed. Argo will reconcile queenswood-platform; Crossplane will reinstall queenswood-keycloak with the new values."

# Roll the listed queenswood Deployments to pick up :latest images.
gcp-k8s-redeploy-svc +components:
    #!/usr/bin/env zsh
    set -e
    # Use after release-images.yml republishes :latest without a chart
    # version bump -- Crossplane Helm Release sees no spec diff and
    # imagePullPolicy: Always only re-pulls on pod create. Components
    # are brick names without the release prefix: `just gcp-k8s-redeploy-svc
    # console api-service`.
    PROJECT_ID=$(just _gcp-project-id)
    KCTX=$(just _gke-ctx)
    for c in {{ components }}; do
      echo "→ deploy/{{ QUEENSWOOD_ENV }}-$c"
      kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} rollout restart deploy/{{ QUEENSWOOD_ENV }}-$c
      kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} rollout status  deploy/{{ QUEENSWOOD_ENV }}-$c
    done

# Print what to create in the Google console, then capture the result into `pass`.
gcp-oauth-client name="google":
    #!/usr/bin/env zsh
    set -euo pipefail
    # Google has no API for creating a Web OAuth client with a custom
    # redirect URI -- `gcloud iap oauth-clients` is IAP-scoped, sets its
    # own redirect URI, and went non-functional in Jan 2026. So the
    # client is created by hand and this recipe owns the two things
    # that are easy to get wrong: the exact redirect URI, and where the
    # result belongs in `pass`.
    #
    # One client per environment, not one shared across them: revoking or
    # rotating a dev client must not touch the one real customers sign in
    # through, and a client still in Google's Testing mode is a different
    # risk object from a published one. The local rig has its own, filed
    # under queenswood/local/dev/auth/clients/.
    PROJECT_ID=$(just _gcp-project-id)
    echo "Create a Web application OAuth client here:"
    echo "  https://console.cloud.google.com/apis/credentials/oauthclient?project=$PROJECT_ID"
    echo
    echo "Name it:                  {{ QUEENSWOOD_ENV }}"
    echo "Authorised redirect URI:  https://keycloak.{{ QUEENSWOOD_DOMAIN }}/realms/queenswood/broker/{{ name }}/endpoint"
    echo
    printf 'Client ID: '
    read client_id
    printf 'Client secret (not echoed): '
    read -s client_secret
    echo
    if [[ -z "$client_id" || -z "$client_secret" ]]; then
      echo "both values are required" >&2; exit 1
    fi
    printf '%s\n' "$client_id" | pass insert -m -f {{ PASS_ENV }}/auth/clients/ids/{{ name }} >/dev/null
    printf '%s\n' "$client_secret" | pass insert -m -f {{ PASS_ENV }}/auth/clients/{{ name }} >/dev/null
    echo "stored {{ PASS_ENV }}/auth/clients/{ids/{{ name }},{{ name }}}"
    echo "now run: just gcp-keycloak-idp {{ name }}"

# Push an IdP's client id + secret from `pass` into the deployed realm.
gcp-keycloak-idp name="google":
    #!/usr/bin/env zsh
    set -euo pipefail
    # Both the client id and the vault expression go over the Admin API,
    # because the operator won't re-run a completed KeycloakRealmImport
    # -- a realm that already exists never picks up a chart change, so
    # it keeps whatever placeholder it was first imported with. Only the
    # expression is sent, never the secret: Keycloak resolves
    # ${vault.<key>} from the mounted file at token exchange.
    KCTX=$(just _gke-ctx)
    KCHOST=https://keycloak.{{ QUEENSWOOD_DOMAIN }}
    client_id=$(pass show {{ PASS_ENV }}/auth/clients/ids/{{ name }} | head -1)
    just gcp-keycloak-vault-secret {{ name }}
    TOK=$(just _keycloak-admin-token)
    idp=$(curl -sf --max-time 15 -H "Authorization: Bearer $TOK" \
      "$KCHOST/admin/realms/queenswood/identity-provider/instances/{{ name }}")
    printf '%s' "$idp" \
      | CLIENT_ID="$client_id" VAULT_KEY='${vault.{{ name }}-client-secret}' python3 -c 'import sys,json,os; d=json.load(sys.stdin); d["config"]["clientId"]=os.environ["CLIENT_ID"]; d["config"]["clientSecret"]=os.environ["VAULT_KEY"]; json.dump(d,sys.stdout)' \
      | curl -sf --max-time 15 -X PUT -H "Authorization: Bearer $TOK" \
          -H 'Content-Type: application/json' --data @- \
          "$KCHOST/admin/realms/queenswood/identity-provider/instances/{{ name }}"
    echo ">>> {{ name }} clientId updated; restarting Keycloak for the vault mount"
    kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} rollout restart statefulset/queenswood-keycloak-keycloak
    kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} rollout status statefulset/queenswood-keycloak-keycloak --timeout=5m

# Print a Keycloak admin access token for the active env.
[private]
_keycloak-admin-token:
    #!/usr/bin/env zsh
    set -euo pipefail
    KCTX=$(just _gke-ctx)
    S=queenswood-keycloak-keycloak-initial-admin
    u=$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get secret $S -o jsonpath='{.data.username}' | base64 -d)
    p=$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get secret $S -o jsonpath='{.data.password}' | base64 -d)
    curl -sf --max-time 15 -d client_id=admin-cli -d "username=$u" \
      --data-urlencode "password=$p" -d grant_type=password \
      "https://keycloak.{{ QUEENSWOOD_DOMAIN }}/realms/master/protocol/openid-connect/token" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])'

# Load an IdP client secret from `pass` into Keycloak's vault Secret.
gcp-keycloak-vault-secret name="google":
    #!/usr/bin/env zsh
    set -euo pipefail
    # Mirrors the testcontainer flow in test-resources' keycloak
    # container config: same `pass` entry, same files-plaintext vault,
    # same <realm>_<key> filename. The secret goes from `pass` straight
    # to the cluster -- never git, never the shell environment.
    KCTX=$(just _gke-ctx)
    secret=$(pass show {{ PASS_ENV }}/auth/clients/{{ name }} | head -1) || {
      echo "{{ PASS_ENV }}/auth/clients/{{ name }} not in pass" >&2; exit 1
    }
    # <realm>_<key>, which the realm references as ${vault.<key>}.
    kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} create secret generic \
      queenswood-keycloak-vault \
      --from-literal=queenswood_{{ name }}-client-secret="$secret" \
      --dry-run=client -o yaml \
      | kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} apply -f -
    echo ">>> stored queenswood_{{ name }}-client-secret in the vault Secret"

# =========================================================================
# Diagnostics
# =========================================================================

# Print Cloud DNS nameservers (paste into registrar).
gcp-dns-check:
    kubectl --context {{ KIND_XP_CLUSTER }} -n {{ QUEENSWOOD_ENV }} \
      get managedzone.dns.gcp.m.upbound.io {{ QUEENSWOOD_DNS_ZONE }} \
      -o jsonpath='{.status.atProvider.nameServers}' \
      | tr ',' '\n' | tr -d '[]"' | sed 's/^ */  /'
    @echo

# Walk the public-traffic chain; one tick/cross/busy line per check.
gcp-health-check:
    #!/usr/bin/env zsh
    MGT=kind-xp-mp
    KCTX=$(just _gke-ctx)
    ok=$'\033[32m✓\033[0m'
    bad=$'\033[31m✗\033[0m'
    busy=$'\033[33m⏳\033[0m'

    # eq label actual expected -- ✓ if match, ✗ if empty, ⏳ otherwise.
    eq() {
      if [[ -z "$2" ]]; then printf "%s %s (missing)\n" "$bad" "$1"
      elif [[ "$2" == "$3" ]]; then printf "%s %s\n" "$ok" "$1"
      else printf "%s %s (%s)\n" "$busy" "$1" "$2"
      fi
    }
    # present label actual -- ✓ if non-empty, ✗ otherwise.
    present() {
      if [[ -n "$2" ]]; then printf "%s %s\n" "$ok" "$1"
      else printf "%s %s (not yet)\n" "$bad" "$1"
      fi
    }
    # is label actual expected -- binary (✓ on match, ✗ otherwise) for
    # terminal checks like HTTP probes where no transitional state exists.
    is() {
      if [[ "$2" == "$3" ]]; then printf "%s %s\n" "$ok" "$1"
      else printf "%s %s (%s)\n" "$bad" "$1" "${2:-no response}"
      fi
    }

    # ---- Crossplane (kind) ----
    kubectl --context $MGT get providers.pkg.crossplane.io \
      -o jsonpath='{range .items[*]}{.metadata.name} {.status.conditions[?(@.type=="Installed")].status} {.status.conditions[?(@.type=="Healthy")].status}{"\n"}{end}' 2>/dev/null \
      | while read name installed healthy; do
        [[ -z "$name" ]] && continue
        if [[ "$installed" == "True" && "$healthy" == "True" ]]; then
          printf "%s provider/%s\n" "$ok" "$name"
        else
          printf "%s provider/%s (installed=%s healthy=%s)\n" "$busy" "$name" "$installed" "$healthy"
        fi
      done

    # ---- GCP infra (kind side) ----
    present "static IP queenswood-ingress-ip allocated" \
      "$(kubectl --context $MGT -n {{ QUEENSWOOD_ENV }} get addresses.compute.gcp.m.upbound.io queenswood-ingress-ip -o jsonpath='{.status.atProvider.address}' 2>/dev/null)"
    eq "ManagedZone {{ QUEENSWOOD_DNS_ZONE }} Ready" \
      "$(kubectl --context $MGT -n {{ QUEENSWOOD_ENV }} get managedzones.dns.gcp.m.upbound.io {{ QUEENSWOOD_DNS_ZONE }} -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)" True
    eq "RecordSet queenswood-apex Synced" \
      "$(kubectl --context $MGT -n {{ QUEENSWOOD_ENV }} get recordsets.dns.gcp.m.upbound.io queenswood-apex -o jsonpath='{.status.conditions[?(@.type=="Synced")].status}' 2>/dev/null)" True
    eq "XPlatform queenswood Ready" \
      "$(kubectl --context $MGT -n crossplane-system get xplatform.platform.queenswood.repldriven.com queenswood -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)" True
    present "XPlatform.status.connectionName populated" \
      "$(kubectl --context $MGT -n crossplane-system get xplatform.platform.queenswood.repldriven.com queenswood -o jsonpath='{.status.connectionName}' 2>/dev/null)"
    eq "Certificate queenswood-cert ACTIVE" \
      "$(kubectl --context $MGT -n {{ QUEENSWOOD_ENV }} get certificate.certificatemanager.gcp.m.upbound.io queenswood-cert -o jsonpath='{.status.atProvider.managed.state}' 2>/dev/null)" ACTIVE
    eq "Certificate queenswood-wildcard-cert ACTIVE" \
      "$(kubectl --context $MGT -n {{ QUEENSWOOD_ENV }} get certificate.certificatemanager.gcp.m.upbound.io queenswood-wildcard-cert -o jsonpath='{.status.atProvider.managed.state}' 2>/dev/null)" ACTIVE

    # ---- GKE workloads ----
    pending=$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get pods --field-selector=status.phase=Pending --no-headers 2>/dev/null | wc -l | tr -d ' ')
    failing=$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get pods --no-headers 2>/dev/null | awk '$3 ~ /(Error|CrashLoopBackOff|ImagePullBackOff)/' | wc -l | tr -d ' ')
    if (( failing > 0 )); then printf "%s pods (%d failing)\n" "$bad" "$failing"
    elif (( pending > 0 )); then printf "%s pods (%d pending)\n" "$busy" "$pending"
    else printf "%s pods\n" "$ok"
    fi
    eq "FDB cluster available" \
      "$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get foundationdbcluster -o jsonpath='{.items[0].status.health.available}' 2>/dev/null)" true
    eq "cloud-sql-proxy ready" \
      "$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get deploy queenswood-keycloak-cloudsql-proxy -o jsonpath='{.status.readyReplicas}/{.status.replicas}' 2>/dev/null)" "1/1"
    # The operator names the StatefulSet <Keycloak CR>-keycloak, so the
    # queenswood-keycloak CR yields queenswood-keycloak-keycloak.
    eq "Keycloak StatefulSet ready" \
      "$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get statefulset queenswood-keycloak-keycloak -o jsonpath='{.status.readyReplicas}/{.status.replicas}' 2>/dev/null)" "1/1"
    eq "Gateway {{ QUEENSWOOD_ENV }}-gateway Programmed" \
      "$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get gateway {{ QUEENSWOOD_ENV }}-gateway -o jsonpath='{.status.conditions[?(@.type=="Programmed")].status}' 2>/dev/null)" True

    # ---- Public DNS + HTTPS ----
    present "dig A {{ QUEENSWOOD_DOMAIN }} @8.8.8.8" \
      "$(dig +short A {{ QUEENSWOOD_DOMAIN }} @8.8.8.8 2>/dev/null)"
    is "Keycloak realm endpoint -> 200" \
      "$(curl -sIo /dev/null -w '%{http_code}' --max-time 5 https://keycloak.{{ QUEENSWOOD_DOMAIN }}/realms/queenswood/.well-known/openid-configuration 2>/dev/null)" 200
    present "dig A console.{{ QUEENSWOOD_DOMAIN }} @8.8.8.8" \
      "$(dig +short A console.{{ QUEENSWOOD_DOMAIN }} @8.8.8.8 2>/dev/null)"
    is "HTTPS console.{{ QUEENSWOOD_DOMAIN }} -> 200" \
      "$(curl -sIo /dev/null -w '%{http_code}' --max-time 5 https://console.{{ QUEENSWOOD_DOMAIN }} 2>/dev/null)" 200
    is "HTTPS {{ QUEENSWOOD_DOMAIN }} -> 200" \
      "$(curl -sIo /dev/null -w '%{http_code}' --max-time 5 https://{{ QUEENSWOOD_DOMAIN }} 2>/dev/null)" 200
    is "HTTP {{ QUEENSWOOD_DOMAIN }} -> 301" \
      "$(curl -sIo /dev/null -w '%{http_code}' --max-time 5 http://{{ QUEENSWOOD_DOMAIN }} 2>/dev/null)" 301
