# One-time org setup: grant the operator orgpolicy.policyAdmin and pin
# compute.skipDefaultNetworkCreation at the org level so every child
# project inherits it. roles/owner does NOT include orgpolicy perms.
gcp-org-bootstrap:
    #!/usr/bin/env zsh
    set -e
    : "${GCLOUD_ORGANIZATION_ID:?must be set}"
    ME=$(gcloud config get-value account)
    echo ">>> granting orgpolicy.policyAdmin to $ME on org $GCLOUD_ORGANIZATION_ID"
    gcloud organizations add-iam-policy-binding "$GCLOUD_ORGANIZATION_ID" \
      --member="user:${ME}" --role="roles/orgpolicy.policyAdmin" --condition=None
    echo ">>> setting compute.skipDefaultNetworkCreation on org"
    printf 'name: organizations/%s/policies/compute.skipDefaultNetworkCreation\nspec:\n  rules:\n    - enforce: true\n' "$GCLOUD_ORGANIZATION_ID" \
      | gcloud org-policies set-policy /dev/stdin

# Create a new GCP project, link billing, return the ID on stdout.
# Assumes the org has compute.skipDefaultNetworkCreation enforced
# (see gcp-org-bootstrap); the policy is inherited by every child
# project so no per-project policy work is needed here.
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

# Authenticate gcloud. `gcp-project-create` sets the active project,
# so this recipe doesn't touch project config.
gcp-login:
    gcloud auth login

# Login to GCP impersonating the crossplane SA
gcp-sa-login:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    SA=crossplane-provider
    SA_EMAIL=${SA}@${PROJECT_ID}.iam.gserviceaccount.com
    gcloud auth application-default login \
      --impersonate-service-account=$SA_EMAIL

# Provision the crossplane-provider service account, IAM bindings, key, and APIs
gcp-bootstrap:
    #!/usr/bin/env zsh
    set -e

    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    SA=crossplane-provider
    SA_EMAIL=${SA}@${PROJECT_ID}.iam.gserviceaccount.com
    ME=$(gcloud config get-value account)

    gcloud config set project $PROJECT_ID

    # Enable the APIs Crossplane will need
    gcloud services enable \
      container.googleapis.com \
      compute.googleapis.com \
      dns.googleapis.com \
      certificatemanager.googleapis.com \
      iam.googleapis.com \
      iamcredentials.googleapis.com \
      cloudresourcemanager.googleapis.com \
      sqladmin.googleapis.com

    # Create the service account Crossplane will act as. Wrapped in
    # an existence check so re-running the recipe is idempotent --
    # `gcloud iam service-accounts create` exits non-zero when the SA
    # already exists, which `set -e` then turns into a script failure.
    # Re-running matters because the role list below evolves over time
    # (e.g. roles/certificatemanager.editor was added retroactively);
    # an existing SA needs to be brought up to the current role set.
    if ! gcloud iam service-accounts describe $SA_EMAIL >/dev/null 2>&1; then
      gcloud iam service-accounts create $SA \
        --display-name "Crossplane Provider"
    fi

    # Grant the SA the roles it needs to manage GKE + networking + DNS
    # + cert manager + CloudSQL. `add-iam-policy-binding` is idempotent
    # on its own, so re-running just no-ops on already-granted roles
    # and adds any newly listed ones.
    for role in \
      roles/container.admin \
      roles/compute.networkAdmin \
      roles/dns.admin \
      roles/certificatemanager.owner \
      roles/cloudsql.admin \
      roles/iam.serviceAccountUser \
      roles/resourcemanager.projectIamAdmin ; do
      gcloud projects add-iam-policy-binding $PROJECT_ID \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="$role"
    done

    # Grant yourself permission to impersonate the SA (replaces key creation)
    gcloud iam service-accounts add-iam-policy-binding $SA_EMAIL \
      --member="user:${ME}" \
      --role="roles/iam.serviceAccountTokenCreator"

# Roll one or more queenswood Deployments to pick up a fresh :latest image.
# Use after `release-images.yml` republishes :latest without a chart version
# bump -- since the Crossplane Helm Release sees no spec diff, nothing
# naturally recreates pods, and `imagePullPolicy: Always` only re-pulls on
# pod create. Components are brick names without the `queenswood-` release
# prefix: `just gcp-rollout bank-app bank-api-service`.
gcp-rollout +components:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    KCTX=gke_${PROJECT_ID}_europe-west2-a_queenswood-gke
    for c in {{ components }}; do
      echo "→ deploy/{{ ENV }}-$c"
      kubectl --context $KCTX -n {{ ENV }}rollout restart deploy/{{ ENV }}-$c
      kubectl --context $KCTX -n {{ ENV }}rollout status  deploy/{{ ENV }}-$c
    done

# Print Cloud DNS nameservers for the queenswood zone (paste into registrar NS records)
gcp-dns-ns:
    kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} \
      get managedzone.dns.gcp.m.upbound.io {{ ENV_ZONE }} \
      -o jsonpath='{.status.atProvider.nameServers}' \
      | tr ',' '\n' | tr -d '[]"' | sed 's/^ */  /'
    @echo

# Walk the queenswood public-traffic chain top-to-bottom; first failure points to where to look
gcp-health-check:
    #!/usr/bin/env zsh
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    MGT=kind-xp-mp
    KCTX=gke_${PROJECT_ID}_europe-west2-a_queenswood-gke

    echo "=== 1. Crossplane providers ($MGT) ==="
    kubectl --context $MGT get providers.pkg.crossplane.io \
      -o custom-columns=NAME:.metadata.name,\
    INSTALLED:'.status.conditions[?(@.type=="Installed")].status',\
    HEALTHY:'.status.conditions[?(@.type=="Healthy")].status' \
      2>/dev/null || echo "  (failed to list providers)"

    echo "\n=== 2. GCP infra (Address / Cloud DNS / GKE) ==="
    echo -n "Address queenswood-ingress-ip: "
    kubectl --context $MGT -n {{ ENV }} get addresses.compute.gcp.m.upbound.io \
      queenswood-ingress-ip -o jsonpath='{.status.atProvider.address}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "ManagedZone {{ ENV_ZONE }} Ready: "
    kubectl --context $MGT -n {{ ENV }} get managedzones.dns.gcp.m.upbound.io {{ ENV_ZONE }} \
      -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "RecordSet queenswood-apex rrdatas: "
    kubectl --context $MGT -n {{ ENV }} get recordsets.dns.gcp.m.upbound.io queenswood-apex \
      -o jsonpath='{.spec.forProvider.rrdatas}{"  Synced="}{.status.conditions[?(@.type=="Synced")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "NodePool queenswood-pool: nodeCount="
    kubectl --context $MGT get nodepools.container.gcp.upbound.io queenswood-pool \
      -o jsonpath='{.status.atProvider.nodeCount}{"  Ready="}{.status.conditions[?(@.type=="Ready")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"

    echo "\n=== 3. GKE nodes + scheduling pressure ==="
    kubectl --context $KCTX get nodes \
      -o custom-columns=NAME:.metadata.name,STATUS:'.status.conditions[?(@.type=="Ready")].status',\
    AGE:.metadata.creationTimestamp 2>/dev/null || echo "  (failed to reach GKE)"
    echo -n "Pods Pending in {{ ENV }} namespace: "
    kubectl --context $KCTX -n {{ ENV }} get pods \
      --field-selector=status.phase=Pending --no-headers 2>/dev/null | wc -l | tr -d ' '

    echo "\n=== 4. Helm release ==="
    helm --kube-context $KCTX -n {{ ENV }} history {{ ENV }} 2>/dev/null \
      | tail -2 || echo "  (release not found)"
    echo "Rendered kinds:"
    helm --kube-context $KCTX -n {{ ENV }} get manifest {{ ENV }} 2>/dev/null \
      | grep -E "^kind:" | sort | uniq -c \
      | grep -E "Gateway|HTTPRoute|Deployment|Job" \
      || echo "  (no manifest)"

    echo "\n=== 5. Workloads ==="
    echo "Pod phases:"
    kubectl --context $KCTX -n {{ ENV }} get pods --no-headers 2>/dev/null \
      | awk '{print $3}' | sort | uniq -c | sed 's/^/  /'
    echo "One-shot Jobs:"
    kubectl --context $KCTX -n {{ ENV }} get jobs --no-headers 2>/dev/null \
      | awk '{printf "  %-50s %s\n", $1, $2}'
    echo -n "FDB cluster available: "
    kubectl --context $KCTX -n {{ ENV }} get foundationdbcluster \
      -o jsonpath='{.items[0].status.health.available}{"\n"}' 2>/dev/null \
      || echo "(no FDB cluster)"
    echo -n "Keycloak CloudSQL XR Ready: "
    kubectl --context $MGT -n {{ ENV }} \
      get xkeycloakdatabase.platform.queenswood.repldriven.com queenswood-keycloak \
      -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "Keycloak cloud-sql-proxy ready: "
    kubectl --context $KCTX -n {{ ENV }} get deploy queenswood-keycloak-cloudsql-proxy \
      -o jsonpath='{.status.readyReplicas}/{.status.replicas}{"\n"}' 2>/dev/null \
      || echo "(not installed)"
    echo -n "Keycloak StatefulSet ready: "
    kubectl --context $KCTX -n {{ ENV }} get statefulset queenswood-keycloak \
      -o jsonpath='{.status.readyReplicas}/{.status.replicas}{"\n"}' 2>/dev/null \
      || echo "(not installed)"
    echo -n "Keycloak realm endpoint: "
    curl -sIo /dev/null -w '%{http_code}\n' --max-time 5 \
      https://keycloak.{{ ENV_DOMAIN }}/realms/queenswood/.well-known/openid-configuration \
      2>/dev/null || echo "(unreachable)"

    echo "\n=== 6. Public-traffic chain on GKE ==="
    echo "Gateways:"
    kubectl --context $KCTX -n {{ ENV }} get gateway -o json 2>/dev/null \
      | jq -r '.items[] | "  \(.metadata.name)  class=\(.spec.gatewayClassName)  address=\((.status.addresses[0].value // "-"))  programmed=\((.status.conditions[]? | select(.type=="Programmed") | .status))"' \
      || echo "  (none)"
    echo "HTTPRoutes:"
    kubectl --context $KCTX -n {{ ENV }} get httproute --no-headers 2>/dev/null \
      | awk '{printf "  %-40s %s\n", $1, $2}' \
      || echo "  (none)"
    echo -n "Certificate Manager cert: "
    kubectl --context kind-xp-mp -n {{ ENV }} \
      get certificate.certificatemanager.gcp.m.upbound.io \
      queenswood-cert -o jsonpath='{.status.atProvider.managed.state}{"\n"}' \
      2>/dev/null || echo "(not provisioned)"

    echo "\n=== 7. Public DNS + HTTPS ==="
    echo -n "dig A {{ ENV_DOMAIN }} @8.8.8.8: "
    dig +short A {{ ENV_DOMAIN }} @8.8.8.8 | tr '\n' ' '; echo
    echo "curl -sI https://{{ ENV_DOMAIN }}:"
    curl -sI --max-time 5 https://{{ ENV_DOMAIN }} 2>&1 | head -3 | sed 's/^/  /'
    echo "curl -sI http://{{ ENV_DOMAIN }} (expect 301):"
    curl -sI --max-time 5 http://{{ ENV_DOMAIN }} 2>&1 | head -3 | sed 's/^/  /'

# Inspect the first queenswood pod whose STATUS column matches `state` (default Init:Error)
gcp-diagnose-pod state="Init:Error":
    #!/usr/bin/env zsh
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    KCTX=gke_${PROJECT_ID}_europe-west2-a_queenswood-gke

    # Fail loudly upfront if kubectl can't reach GKE -- the most common cause
    # is gke-gcloud-auth-plugin not on PATH in this non-interactive shell.
    if ! kubectl --context $KCTX cluster-info >/dev/null 2>&1; then
      echo "ERROR: kubectl can't reach $KCTX. Likely causes:" >&2
      echo "  - gke-gcloud-auth-plugin missing from PATH in this shell" >&2
      echo "  - gcloud creds expired (try: gcloud container clusters \\" >&2
      echo "      get-credentials queenswood-gke --zone=europe-west2-a \\" >&2
      echo "      --project=$PROJECT_ID)" >&2
      echo "Full error:" >&2
      kubectl --context $KCTX cluster-info >&2
      exit 1
    fi

    POD=$(kubectl --context $KCTX -n {{ ENV }}get pods --no-headers \
      | awk -v state="{{ state }}" '$3 == state {print $1; exit}')

    if [[ -z "$POD" ]]; then
      echo "No pods in queenswood namespace matched STATUS={{ state }}"
      echo "Current status counts:"
      kubectl --context $KCTX -n {{ ENV }}get pods --no-headers \
        | awk '{print "  " $3}' | sort | uniq -c
      echo "Try: just gcp-diagnose-pod 'Init:CrashLoopBackOff'  or  ImagePullBackOff  or  'Init:1/2'"
      exit 0
    fi

    echo "=== Pod $POD (STATUS={{ state }}) ==="
    kubectl --context $KCTX -n {{ ENV }}get pod $POD

    echo "\n=== Init container states ==="
    kubectl --context $KCTX -n {{ ENV }}get pod $POD -o json \
      | jq -r '.status.initContainerStatuses[]? |
          "  \(.name): " +
          (if .state.running then "running"
           elif .state.terminated then "terminated exit=\(.state.terminated.exitCode) reason=\(.state.terminated.reason // "-")"
           elif .state.waiting then "waiting reason=\(.state.waiting.reason)"
           else "unknown" end)'

    echo "\n=== Recent events ==="
    kubectl --context $KCTX -n {{ ENV }}describe pod $POD \
      | sed -n '/^Events:/,$p' | head -20

    echo "\n=== Init container logs (last 30 lines each) ==="
    for c in $(kubectl --context $KCTX -n {{ ENV }}get pod $POD \
        -o jsonpath='{.spec.initContainers[*].name}'); do
      echo "--- init: $c ---"
      kubectl --context $KCTX -n {{ ENV }}logs $POD -c $c --tail=30 2>&1 \
        | sed 's/^/  /'
    done

    echo "\n=== Main container logs (--previous if it crashed) ==="
    for c in $(kubectl --context $KCTX -n {{ ENV }}get pod $POD \
        -o jsonpath='{.spec.containers[*].name}'); do
      echo "--- container: $c ---"
      kubectl --context $KCTX -n {{ ENV }}logs $POD -c $c --tail=30 --previous 2>&1 \
        | sed 's/^/  /'
    done


# Pre-create the {{ ENV_ZONE }} ManagedZone as the user (who's a
# verified domain owner) so Crossplane (calling as the impersonated
# SA via ADC) can Observe it instead of Create. Cloud DNS's
# domain-ownership check rejects SA-initiated zone creates: the SA
# isn't a verified owner, and per-cold-start re-verification per SA
# email is impractical. If the user isn't yet verified for the parent
# domain, the create surfaces an "ownership" error -- launch
# `gcloud domains verify` and rerun.
gcp-precreate-zone:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    DNS={{ ENV_DOMAIN }}.
    NAME={{ ENV_ZONE }}
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
        echo ">>> after verifying, rerun: just gcp-precreate-zone"
        exit 1
      fi
      cat /tmp/dns-err; exit 1
    fi

# Cold start: existing GCP project + no kind cluster -> green stack
up-from-zero:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create' first" >&2; exit 1; }
    just gcp-login
    just gcp-bootstrap
    # cloud-sql-proxy's Workload Identity binding. The K8s SA the
    # binding references doesn't have to exist yet (GCP's IAM API
    # doesn't validate the cluster side), so getting it in here means
    # by the time the queenswood-keycloak chart's proxy Deployment
    # comes up its impersonation is already approved.
    just gcp-keycloak-cloudsql-identity
    just gcp-precreate-zone
    just kind-xp-up
    just gcp-sa-login
    just kind-xp-install-gcp-secret
    kubectl --context {{ KIND_XP_CLUSTER }} apply -f infra/bootstrap/root-app.yml
    just kind-xp-install-gcp-config
    just kind-xp-wait-ready
    # Block on Crossplane finishing the CloudSQL provision (so the
    # XR's `status.connectionName` is populated), then pin both
    # connectionName and the WI SA email into values-gcp.yaml and
    # commit + push so Argo reconciles queenswood-keycloak with the
    # real values.
    just gcp-keycloak-cloudsql-pin-values
    just gcp-health-check

# Tear down kind + the expensive GCP resources (GKE cluster, ingress IP,
# cert, dns-authorization, VPC) and the records they manage in the DNS
# zone. KEEPS: the GCP project, the crossplane-provider SA + IAM, the
# DNS ManagedZone ({{ ENV_ZONE }}) and its NS records at the registrar.
# Rationale: GKE is the only line item costing real money at rest;
# everything kept is free, holds the dnsName claim against Cloud DNS's
# NS-pool exhaustion, and avoids re-verifying domain ownership each cycle.
# Use `just down-and-delete-project` to fully retire the project + zone.
down-to-zero:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; nothing to tear down" >&2; exit 1; }
    REGION=europe-west2
    ZONE_NAME={{ ENV_ZONE }}
    DOMAIN={{ ENV_DOMAIN }}.
    GKE_CTX="gke_${PROJECT_ID}_${REGION}-a_queenswood-gke"

    # 1. Drain workload namespaces on GKE BEFORE anything else. The
    # GKE CSI driver (pd.csi.storage.gke.io) only DeleteDisk's a PV
    # while the cluster is alive; if the cluster is destroyed with
    # PVCs still around, the underlying PDs leak as orphans and
    # chew through regional SSD_TOTAL_GB on the next up-from-zero
    # (~180 GiB per cycle from pulsar zk + bookie + fdb). Drain
    # must happen while Crossplane (in kind) is still up so that
    # any namespaced Crossplane managed resources can have their
    # finalizers cleared.
    if kubectl --context "$GKE_CTX" get ns queenswood-test >/dev/null 2>&1; then
      echo ">>> draining queenswood-test (lets CSI free PVs cleanly)"
      kubectl --context "$GKE_CTX" delete ns queenswood-test \
        --wait=true --timeout=5m \
        || echo "  (drain timed out -- some PDs may leak)" >&2
    fi

    # 2. Kill kind so Crossplane stops reconciling -- otherwise the
    # gcloud deletes below race against selfHeal trying to recreate them.
    just kind-xp-down || true

    # 3. Delete in dependency order. --quiet + || true so a missing
    # resource (idempotent re-run) doesn't abort the rest.
    echo ">>> deleting GKE cluster (most of the bill)"
    gcloud container clusters delete queenswood-gke \
      --zone="${REGION}-a" --project="$PROJECT_ID" --quiet 2>/dev/null || true

    echo ">>> deleting ingress IP"
    gcloud compute addresses delete queenswood-ingress-ip \
      --region="$REGION" --project="$PROJECT_ID" --quiet 2>/dev/null || true

    echo ">>> deleting Certificate + DNSAuthorization"
    gcloud certificate-manager certificates delete queenswood-cert \
      --location="$REGION" --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud certificate-manager dns-authorizations delete queenswood-dns-auth \
      --location="$REGION" --project="$PROJECT_ID" --quiet 2>/dev/null || true

    echo ">>> deleting subnets + VPC"
    gcloud compute networks subnets delete queenswood-subnet \
      --region="$REGION" --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud compute networks subnets delete queenswood-proxy-only \
      --region="$REGION" --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud compute networks delete queenswood-vpc \
      --project="$PROJECT_ID" --quiet 2>/dev/null || true

    # 4. Drop the apex A and the cert-validation CNAME from the zone;
    # keep the zone itself (and its NS records) so the registrar
    # configuration stays valid across cycles.
    echo ">>> deleting apex A + cert CNAME (keeping zone)"
    gcloud dns record-sets delete "$DOMAIN" --type=A --zone="$ZONE_NAME" \
      --project="$PROJECT_ID" --quiet 2>/dev/null || true
    gcloud dns record-sets list --zone="$ZONE_NAME" --project="$PROJECT_ID" \
        --filter="type=CNAME" --format='value(name)' 2>/dev/null \
      | while read n; do
          gcloud dns record-sets delete "$n" --type=CNAME --zone="$ZONE_NAME" \
            --project="$PROJECT_ID" --quiet 2>/dev/null || true
        done

    # 5. Prune local kubeconfig entry so contexts don't accumulate.
    kubectl config delete-context "$GKE_CTX" 2>/dev/null || true
    kubectl config delete-cluster "$GKE_CTX" 2>/dev/null || true
    kubectl config delete-user    "$GKE_CTX" 2>/dev/null || true

    echo ">>> kept: project, SA, IAM, DNS zone, registrar NS. Run 'just up-from-zero' to come back."

# Full retirement: down-to-zero + delete the DNS zone (releasing the
# dnsName claim immediately rather than waiting on the project's 30-day
# soft-delete window) + delete the project. Rarely needed -- this burns
# NS pool allocations and forces domain re-verification next time. Use
# only when retiring the deployment entirely.
down-and-delete-project:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; nothing to delete" >&2; exit 1; }
    just down-to-zero

    # Empty the zone (zone delete refuses if non-SOA/NS records remain),
    # then delete the zone itself. Doing this BEFORE project delete is
    # what releases the dnsName claim immediately.
    echo ">>> emptying + deleting {{ ENV_ZONE }}"
    gcloud dns record-sets list --zone={{ ENV_ZONE }} --project="$PROJECT_ID" \
        --format='value(name,type)' 2>/dev/null \
      | while read name type; do
          [[ "$type" == "SOA" || "$type" == "NS" ]] && continue
          gcloud dns record-sets delete "$name" --type="$type" \
            --zone={{ ENV_ZONE }} --project="$PROJECT_ID" --quiet 2>/dev/null || true
        done
    gcloud dns managed-zones delete {{ ENV_ZONE }} \
      --project="$PROJECT_ID" --quiet 2>/dev/null || true

    echo ">>> deleting GCP project $PROJECT_ID"
    gcloud projects delete "$PROJECT_ID" --quiet
    gcloud config unset project >/dev/null 2>&1 || true
    echo ">>> recover within 30 days: gcloud projects undelete $PROJECT_ID"

# Block until each Argo Application reaches Healthy
kind-xp-wait-ready timeout="2700":
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    for app in crossplane-providers crossplane-configs crossplane-xrds \
               gcp-network gcp-cluster queenswood-platform; do
      echo ">>> $app"
      # Wait for Argo to discover the Application before waiting on its health
      until kubectl --context {{ KIND_XP_CLUSTER }} -n argocd \
            get application/$app >/dev/null 2>&1; do sleep 5; done
      # queenswood-platform's Healthy gate depends on the GCP cert
      # validating, which depends on NS propagation from the registrar.
      # So we surface NS records as soon as the ManagedZone is queryable
      # (sync-wave 4 inside the chart), BEFORE waiting for the App to be
      # Healthy — otherwise the wait deadlocks until the user manually
      # discovers they need to paste NS records.
      if [[ "$app" == "queenswood-platform" ]]; then
        until kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} get \
              managedzone.dns.gcp.m.upbound.io {{ ENV_ZONE }} >/dev/null 2>&1; do
          sleep 5
        done
        until [[ -n "$(kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} get \
              managedzone.dns.gcp.m.upbound.io {{ ENV_ZONE }} \
              -o jsonpath='{.status.atProvider.nameServers}' 2>/dev/null)" ]]; do
          sleep 5
        done
        echo
        echo "------------------------------------------------------"
        echo "ACTION: paste these 4 nameservers into your registrar's"
        echo "NS records for {{ ENV_DOMAIN }} (trailing dots"
        echo "optional). cert validation gates on this propagating."
        kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} \
          get managedzone.dns.gcp.m.upbound.io {{ ENV_ZONE }} \
          -o jsonpath='{.status.atProvider.nameServers}' \
          | tr ',' '\n' | tr -d '[]"' | sed 's/^ */  /'
        echo "------------------------------------------------------"
        echo
      fi
      kubectl --context {{ KIND_XP_CLUSTER }} -n argocd wait \
        --for=jsonpath='{.status.health.status}'=Healthy \
        application/$app --timeout={{ timeout }}s
      # As soon as GKE is up, write its kubeconfig context locally so
      # k9s/kubectl/gcp-* recipes can target the cluster during the rest
      # of the cold-start (cert provisioning, queenswood Release, etc.).
      # Crossplane has its own kubeconfig for the Helm Release; this is
      # purely for the operator's terminal.
      if [[ "$app" == "gcp-cluster" ]]; then
        gcloud container clusters get-credentials queenswood-gke \
          --zone=europe-west2-a --project="$PROJECT_ID"
      fi
    done


# Provision the GCP service account the cloud-sql-proxy pod authenticates
# as, bound to the K8s ServiceAccount via Workload Identity. Idempotent
# -- safe to re-run on `up-from-zero`. The SA email itself is pinned
# into values-gcp.yaml by `gcp-keycloak-cloudsql-pin-values` once
# CloudSQL has finished provisioning.
gcp-keycloak-cloudsql-identity:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project; run 'just gcp-project-create'" >&2; exit 1; }
    # GCP IAM caps account IDs at 30 chars, so the cloud-side SA is
    # shorter than the K8s-side one. The Workload Identity binding ties
    # them together by full email + namespace/SA tuple — they don't
    # have to share a name.
    SA=keycloak-sql-proxy
    SA_EMAIL=${SA}@${PROJECT_ID}.iam.gserviceaccount.com
    K8S_SA=queenswood-keycloak-cloudsql-proxy
    NS={{ ENV }}

    if ! gcloud iam service-accounts describe $SA_EMAIL >/dev/null 2>&1; then
      gcloud iam service-accounts create $SA \
        --display-name "Keycloak Cloud SQL Proxy"
    fi

    gcloud projects add-iam-policy-binding $PROJECT_ID \
      --member="serviceAccount:${SA_EMAIL}" \
      --role="roles/cloudsql.client"

    # Workload Identity: the K8s ServiceAccount the proxy pod runs as
    # impersonates the GCP SA. Format below is the canonical
    # `<project>.svc.id.goog[<namespace>/<k8s-sa>]` member.
    gcloud iam service-accounts add-iam-policy-binding $SA_EMAIL \
      --role="roles/iam.workloadIdentityUser" \
      --member="serviceAccount:${PROJECT_ID}.svc.id.goog[${NS}/${K8S_SA}]"

    echo "GCP SA + WI binding ready: $SA_EMAIL"

# Wait for Crossplane to finish provisioning the CloudSQL instance,
# then pin both `postgresql.external.proxy.gcpServiceAccount` and
# `.connectionName` into values-gcp.yaml and commit + push so Argo
# picks them up on its next reconcile. Idempotent: if the file
# already holds the right values, the recipe no-ops (no empty
# commits).
#
# CloudSQL provisioning routinely takes 5-10 minutes from the XR
# being created, so the wait timeout is 15m.
gcp-keycloak-cloudsql-pin-values:
    #!/usr/bin/env zsh
    set -e
    PROJECT_ID=$(gcloud config get project 2>/dev/null)
    [[ -n "$PROJECT_ID" ]] || { echo "no active gcloud project" >&2; exit 1; }
    XR_NAME=queenswood-keycloak
    VALUES=infra/helm/queenswood-keycloak/values-gcp.yaml
    # Matches the SA name set in `gcp-keycloak-cloudsql-identity`
    # (kept short to fit GCP's 30-char IAM account-id cap).
    SA_EMAIL=keycloak-sql-proxy@${PROJECT_ID}.iam.gserviceaccount.com

    echo "Waiting for XKeycloakDatabase/$XR_NAME status.connectionName (up to 15m)..."
    CONN=""
    deadline=$(( $(date +%s) + 900 ))
    while [[ -z "$CONN" && $(date +%s) -lt $deadline ]]; do
      CONN=$(kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} \
               get xkeycloakdatabase.platform.queenswood.repldriven.com $XR_NAME \
               -o jsonpath='{.status.connectionName}' 2>/dev/null || true)
      [[ -z "$CONN" ]] && sleep 10
    done
    if [[ -z "$CONN" ]]; then
      echo "" >&2
      echo "Timed out waiting for status.connectionName on XKeycloakDatabase/$XR_NAME." >&2
      echo "Dumping diagnostic state:" >&2
      echo "" >&2
      echo "--- XR conditions ---" >&2
      kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} \
        get xkeycloakdatabase.platform.queenswood.repldriven.com $XR_NAME \
        -o jsonpath='{range .status.conditions[*]}{.type}={.status}  reason={.reason}  message={.message}{"\n"}{end}' >&2 || true
      echo "" >&2
      echo "--- Composed resources ---" >&2
      kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} \
        get xkeycloakdatabase.platform.queenswood.repldriven.com $XR_NAME \
        -o jsonpath='{range .spec.resourceRefs[*]}{.kind}/{.name}{"\n"}{end}' >&2 || true
      echo "" >&2
      echo "--- DatabaseInstance state ---" >&2
      kubectl --context {{ KIND_XP_CLUSTER }} get databaseinstance.sql.gcp.m.upbound.io \
        -l crossplane.io/composite=$XR_NAME \
        -o jsonpath='{range .items[*]}{.metadata.name}: synced={.status.conditions[?(@.type=="Synced")].status} ready={.status.conditions[?(@.type=="Ready")].status} message={.status.conditions[?(@.type=="Synced")].message}{"\n"}{end}' >&2 || true
      echo "" >&2
      echo "Full XR yaml:" >&2
      echo "  kubectl --context {{ KIND_XP_CLUSTER }} -n {{ ENV }} get xkeycloakdatabase $XR_NAME -o yaml" >&2
      exit 1
    fi
    echo "Resolved connectionName: $CONN"
    echo "Pinning gcpServiceAccount:  $SA_EMAIL"

    cur_conn=$(yq -r '.postgresql.external.proxy.connectionName' $VALUES)
    cur_sa=$(yq -r '.postgresql.external.proxy.gcpServiceAccount' $VALUES)
    if [[ "$cur_conn" == "$CONN" && "$cur_sa" == "$SA_EMAIL" ]]; then
      echo "$VALUES already pinned; skipping commit."
      exit 0
    fi

    # Use sed not `yq -y -i` — kislyuk/yq round-trips through JSON
    # and strips YAML comments. The two keys live at fixed paths
    # and are unique in this file, so anchored sed is safe.
    sed -i.bak -E "s|^([[:space:]]*connectionName:).*|\1 $CONN|" $VALUES
    sed -i.bak -E "s|^([[:space:]]*gcpServiceAccount:).*|\1 $SA_EMAIL|" $VALUES
    rm -f "${VALUES}.bak"

    git add $VALUES
    git commit -m "values-gcp: pin queenswood-keycloak CloudSQL connection + WI SA"
    git push
    echo "Pushed. Argo will reconcile queenswood-keycloak shortly."
