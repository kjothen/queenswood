# Create a new GCP project, link billing, return the ID on stdout
gcp-project-create suffix=`openssl rand -hex 3`:
    #!/usr/bin/env zsh
    set -e
    # Logs to stderr so `$(just gcp-project-create)` captures only the ID
    PROJECT_ID="queenswood-{{ suffix }}"
    echo ">>> creating project $PROJECT_ID" >&2
    gcloud projects create "$PROJECT_ID" --name=queenswood >&2
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

# Authenticate gcloud and pin the active project to $GOOGLE_CLOUD_PROJECT_ID
gcp-login:
    gcloud auth login
    gcloud config set project $GOOGLE_CLOUD_PROJECT_ID

# Login to GCP impersonating the crossplane SA
gcp-sa-login:
    #!/usr/bin/env zsh
    set -e    
    PROJECT_ID=$GOOGLE_CLOUD_PROJECT_ID
    SA=crossplane-provider
    SA_EMAIL=${SA}@${PROJECT_ID}.iam.gserviceaccount.com
    
    gcloud auth application-default login \
      --impersonate-service-account=$SA_EMAIL
    gcloud config set project $GOOGLE_CLOUD_PROJECT_ID

# Provision the crossplane-provider service account, IAM bindings, key, and APIs
gcp-bootstrap:
    #!/usr/bin/env zsh
    set -e

    PROJECT_ID=$GOOGLE_CLOUD_PROJECT_ID
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
      cloudresourcemanager.googleapis.com

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
    # + cert manager. `add-iam-policy-binding` is idempotent on its own,
    # so re-running just no-ops on already-granted roles and adds any
    # newly listed ones.
    for role in \
      roles/container.admin \
      roles/compute.networkAdmin \
      roles/dns.admin \
      roles/certificatemanager.owner \
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
    : "${GOOGLE_CLOUD_PROJECT_ID:?must be set}"
    set -e
    KCTX=gke_${GOOGLE_CLOUD_PROJECT_ID}_europe-west2-a_queenswood-gke
    for c in {{ components }}; do
      echo "→ deploy/queenswood-$c"
      kubectl --context $KCTX -n queenswood rollout restart deploy/queenswood-$c
      kubectl --context $KCTX -n queenswood rollout status  deploy/queenswood-$c
    done

# Print Cloud DNS nameservers for the queenswood zone (paste into registrar NS records)
gcp-dns-ns:
    kubectl --context kind-xp-mp get managedzone.dns.gcp.upbound.io queenswood-zone \
      -o jsonpath='{.status.atProvider.nameServers}{"\n"}'

# Walk the queenswood public-traffic chain top-to-bottom; first failure points to where to look
gcp-health-check:
    #!/usr/bin/env zsh
    : "${GOOGLE_CLOUD_PROJECT_ID:?must be set}"
    MGT=kind-xp-mp
    KCTX=gke_${GOOGLE_CLOUD_PROJECT_ID}_europe-west2-a_queenswood-gke

    echo "=== 1. Crossplane providers ($MGT) ==="
    kubectl --context $MGT get providers.pkg.crossplane.io \
      -o custom-columns=NAME:.metadata.name,\
    INSTALLED:'.status.conditions[?(@.type=="Installed")].status',\
    HEALTHY:'.status.conditions[?(@.type=="Healthy")].status' \
      2>/dev/null || echo "  (failed to list providers)"

    echo "\n=== 2. GCP infra (Address / Cloud DNS / GKE) ==="
    echo -n "Address queenswood-ingress-ip: "
    kubectl --context $MGT get addresses.compute.gcp.upbound.io \
      queenswood-ingress-ip -o jsonpath='{.status.atProvider.address}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "ManagedZone queenswood-zone Ready: "
    kubectl --context $MGT get managedzones.dns.gcp.upbound.io queenswood-zone \
      -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "RecordSet queenswood-apex rrdatas: "
    kubectl --context $MGT get recordsets.dns.gcp.upbound.io queenswood-apex \
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
    echo -n "Pods Pending in queenswood namespace: "
    kubectl --context $KCTX -n queenswood get pods \
      --field-selector=status.phase=Pending --no-headers 2>/dev/null | wc -l | tr -d ' '

    echo "\n=== 4. Helm release ==="
    helm --kube-context $KCTX -n queenswood history queenswood 2>/dev/null \
      | tail -2 || echo "  (release not found)"
    echo "Rendered kinds:"
    helm --kube-context $KCTX -n queenswood get manifest queenswood 2>/dev/null \
      | grep -E "^kind:" | sort | uniq -c \
      | grep -E "Gateway|HTTPRoute|Deployment|Job" \
      || echo "  (no manifest)"

    echo "\n=== 5. Workloads ==="
    echo "Pod phases:"
    kubectl --context $KCTX -n queenswood get pods --no-headers 2>/dev/null \
      | awk '{print $3}' | sort | uniq -c | sed 's/^/  /'
    echo "One-shot Jobs:"
    kubectl --context $KCTX -n queenswood get jobs --no-headers 2>/dev/null \
      | awk '{printf "  %-50s %s\n", $1, $2}'
    echo -n "FDB cluster available: "
    kubectl --context $KCTX -n queenswood get foundationdbcluster \
      -o jsonpath='{.items[0].status.health.available}{"\n"}' 2>/dev/null \
      || echo "(no FDB cluster)"

    echo "\n=== 6. Public-traffic chain on GKE ==="
    echo "Gateways:"
    kubectl --context $KCTX -n queenswood get gateway -o json 2>/dev/null \
      | jq -r '.items[] | "  \(.metadata.name)  class=\(.spec.gatewayClassName)  address=\((.status.addresses[0].value // "-"))  programmed=\((.status.conditions[]? | select(.type=="Programmed") | .status))"' \
      || echo "  (none)"
    echo "HTTPRoutes:"
    kubectl --context $KCTX -n queenswood get httproute --no-headers 2>/dev/null \
      | awk '{printf "  %-40s %s\n", $1, $2}' \
      || echo "  (none)"
    echo -n "Certificate Manager cert: "
    kubectl --context kind-xp-mp get certificate.certificatemanager.gcp.upbound.io \
      queenswood-cert -o jsonpath='{.status.atProvider.managed.state}{"\n"}' \
      2>/dev/null || echo "(not provisioned)"

    echo "\n=== 7. Public DNS + HTTPS ==="
    echo -n "dig A queenswood.repldriven.com @8.8.8.8: "
    dig +short A queenswood.repldriven.com @8.8.8.8 | tr '\n' ' '; echo
    echo "curl -sI https://queenswood.repldriven.com:"
    curl -sI --max-time 5 https://queenswood.repldriven.com 2>&1 | head -3 | sed 's/^/  /'
    echo "curl -sI http://queenswood.repldriven.com (expect 301):"
    curl -sI --max-time 5 http://queenswood.repldriven.com 2>&1 | head -3 | sed 's/^/  /'

# Inspect the first queenswood pod whose STATUS column matches `state` (default Init:Error)
gcp-diagnose-pod state="Init:Error":
    #!/usr/bin/env zsh
    : "${GOOGLE_CLOUD_PROJECT_ID:?must be set}"
    KCTX=gke_${GOOGLE_CLOUD_PROJECT_ID}_europe-west2-a_queenswood-gke

    # Fail loudly upfront if kubectl can't reach GKE -- the most common cause
    # is gke-gcloud-auth-plugin not on PATH in this non-interactive shell.
    if ! kubectl --context $KCTX cluster-info >/dev/null 2>&1; then
      echo "ERROR: kubectl can't reach $KCTX. Likely causes:" >&2
      echo "  - gke-gcloud-auth-plugin missing from PATH in this shell" >&2
      echo "  - gcloud creds expired (try: gcloud container clusters \\" >&2
      echo "      get-credentials queenswood-gke --zone=europe-west2-a \\" >&2
      echo "      --project=\$GOOGLE_CLOUD_PROJECT_ID)" >&2
      echo "Full error:" >&2
      kubectl --context $KCTX cluster-info >&2
      exit 1
    fi

    POD=$(kubectl --context $KCTX -n queenswood get pods --no-headers \
      | awk -v state="{{ state }}" '$3 == state {print $1; exit}')

    if [[ -z "$POD" ]]; then
      echo "No pods in queenswood namespace matched STATUS={{ state }}"
      echo "Current status counts:"
      kubectl --context $KCTX -n queenswood get pods --no-headers \
        | awk '{print "  " $3}' | sort | uniq -c
      echo "Try: just gcp-diagnose-pod 'Init:CrashLoopBackOff'  or  ImagePullBackOff  or  'Init:1/2'"
      exit 0
    fi

    echo "=== Pod $POD (STATUS={{ state }}) ==="
    kubectl --context $KCTX -n queenswood get pod $POD

    echo "\n=== Init container states ==="
    kubectl --context $KCTX -n queenswood get pod $POD -o json \
      | jq -r '.status.initContainerStatuses[]? |
          "  \(.name): " +
          (if .state.running then "running"
           elif .state.terminated then "terminated exit=\(.state.terminated.exitCode) reason=\(.state.terminated.reason // "-")"
           elif .state.waiting then "waiting reason=\(.state.waiting.reason)"
           else "unknown" end)'

    echo "\n=== Recent events ==="
    kubectl --context $KCTX -n queenswood describe pod $POD \
      | sed -n '/^Events:/,$p' | head -20

    echo "\n=== Init container logs (last 30 lines each) ==="
    for c in $(kubectl --context $KCTX -n queenswood get pod $POD \
        -o jsonpath='{.spec.initContainers[*].name}'); do
      echo "--- init: $c ---"
      kubectl --context $KCTX -n queenswood logs $POD -c $c --tail=30 2>&1 \
        | sed 's/^/  /'
    done

    echo "\n=== Main container logs (--previous if it crashed) ==="
    for c in $(kubectl --context $KCTX -n queenswood get pod $POD \
        -o jsonpath='{.spec.containers[*].name}'); do
      echo "--- container: $c ---"
      kubectl --context $KCTX -n queenswood logs $POD -c $c --tail=30 --previous 2>&1 \
        | sed 's/^/  /'
    done


# Cold start: existing GCP project + no kind cluster -> green stack
up-from-zero:
    #!/usr/bin/env zsh
    set -e
    : "${GOOGLE_CLOUD_PROJECT_ID:?must be set (see: just gcp-project-create)}"
    just gcp-login
    just gcp-bootstrap
    just kind-xp-up
    just gcp-sa-login
    just kind-xp-install-gcp-secret
    kubectl --context {{ KIND_XP_CLUSTER }} apply -f infra/bootstrap/root-app.yml
    just kind-xp-wait-ready
    just gcp-health-check

# Nuke the kind cluster + the GCP project (recoverable for 30 days)
down-to-zero:
    #!/usr/bin/env zsh
    set -e
    : "${GOOGLE_CLOUD_PROJECT_ID:?must be set}"
    just kind-xp-down || true
    echo ">>> deleting GCP project $GOOGLE_CLOUD_PROJECT_ID"
    gcloud projects delete "$GOOGLE_CLOUD_PROJECT_ID" --quiet
    echo ">>> recover within 30 days: gcloud projects undelete $GOOGLE_CLOUD_PROJECT_ID"

# Block until each Argo Application reaches Healthy
kind-xp-wait-ready timeout="2700":
    #!/usr/bin/env zsh
    set -e
    for app in crossplane-providers crossplane-configs crossplane-xrds \
               gcp-network gcp-cluster gcp-dns gcp-cert gcp-records \
               queenswood; do
      echo ">>> $app"
      # Wait for Argo to discover the Application before waiting on its health
      until kubectl --context {{ KIND_XP_CLUSTER }} -n argocd \
            get application/$app >/dev/null 2>&1; do sleep 5; done
      kubectl --context {{ KIND_XP_CLUSTER }} -n argocd wait \
        --for=jsonpath='{.status.health.status}'=Healthy \
        application/$app --timeout={{ timeout }}s
    done
