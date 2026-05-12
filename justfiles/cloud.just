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

    # Create the service account Crossplane will act as
    gcloud iam service-accounts create $SA \
      --display-name "Crossplane Provider"

    # Grant the SA the roles it needs to manage GKE + networking + DNS + cert manager
    for role in \
      roles/container.admin \
      roles/compute.networkAdmin \
      roles/dns.admin \
      roles/certificatemanager.editor \
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

    echo "\n=== 2. GCP infra (GlobalAddress / Cloud DNS / GKE) ==="
    echo -n "GlobalAddress queenswood-ingress-ip: "
    kubectl --context $MGT get globaladdresses.compute.gcp.upbound.io \
      queenswood-ingress-ip -o jsonpath='{.status.atProvider.address}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "ManagedZone queenswood-zone Ready: "
    kubectl --context $MGT get managedzone queenswood-zone \
      -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "RecordSet queenswood-apex rrdatas: "
    kubectl --context $MGT get recordset queenswood-apex \
      -o jsonpath='{.spec.forProvider.rrdatas}{"  Synced="}{.status.conditions[?(@.type=="Synced")].status}{"\n"}' \
      2>/dev/null || echo "(not found)"
    echo -n "NodePool queenswood-pool: nodeCount="
    kubectl --context $MGT get nodepool queenswood-pool \
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
    helm --kube-context $KCTX -n queenswood history queenswood-bank 2>/dev/null \
      | tail -2 || echo "  (release not found)"
    echo "Rendered kinds:"
    helm --kube-context $KCTX -n queenswood get manifest queenswood-bank 2>/dev/null \
      | grep -E "^kind:" | sort | uniq -c \
      | grep -E "Ingress|ManagedCertificate|FrontendConfig|Deployment|Job" \
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
    echo "Ingresses:"
    kubectl --context $KCTX -n queenswood get ingress --no-headers 2>/dev/null \
      | awk '{printf "  %-30s class=%-6s address=%s\n", $1, $2, $4}'
    echo -n "ManagedCertificate: "
    kubectl --context $KCTX -n queenswood get managedcertificate \
      -o jsonpath='{.items[0].metadata.name}={.items[0].status.certificateStatus}{"\n"}' \
      2>/dev/null || echo "(none)"
    echo -n "bank-app Service NEG annotation: "
    kubectl --context $KCTX -n queenswood get svc queenswood-bank-bank-app \
      -o jsonpath='{.metadata.annotations.cloud\.google\.com/neg}{"\n"}' \
      2>/dev/null || echo "(no bank-app service)"

    echo "\n=== 7. Public DNS + HTTPS ==="
    echo -n "dig A queenswood.repldriven.com @8.8.8.8: "
    dig +short A queenswood.repldriven.com @8.8.8.8 | tr '\n' ' '; echo
    echo "curl -sI https://queenswood.repldriven.com:"
    curl -sI --max-time 5 https://queenswood.repldriven.com 2>&1 | head -3 | sed 's/^/  /'
    echo "curl -sI http://queenswood.repldriven.com (expect 301):"
    curl -sI --max-time 5 http://queenswood.repldriven.com 2>&1 | head -3 | sed 's/^/  /'
