# Render the Helm chart locally (does not install)
helm-template tag="dev":
    helm dependency update infra/helm/queenswood
    helm template queenswood infra/helm/queenswood \
      --set image.tag={{ tag }} \
      --set secrets.adminApiKey=template-render

# Lint and schema-validate the Helm chart without a cluster
helm-validate tag="dev":
    # CRDs (FoundationDBCluster etc.) are skipped via -ignore-missing-schemas.
    helm dependency update infra/helm/queenswood
    helm lint infra/helm/queenswood --set secrets.adminApiKey=lint
    helm template queenswood infra/helm/queenswood \
      --set image.tag={{ tag }} \
      --set secrets.adminApiKey=template-render \
      | kubeconform -strict -ignore-missing-schemas -summary

# Install or upgrade the chart; rotates the admin API key only on first install
helm-install tag="dev" admin_key="" registry="ghcr.io/kjothen":
    #!/usr/bin/env zsh
    # Reuses the existing Secret value on subsequent installs so the admin API key is stable
    # across deploys; pass `admin_key=...` to rotate. `registry` lets `kind-up` point at
    # the local registry so containerd pulls from there.
    set -e
    key={{ admin_key }}
    if [[ -z "$key" ]]; then
      key=$(kubectl -n queenswood get secret queenswood-admin-api-key \
        -o jsonpath='{.data.MONO_ADMIN_API_KEY}' 2>/dev/null \
        | base64 -d || true)
      [[ -z "$key" ]] && key=$(openssl rand -hex 16)
    fi
    helm dependency update infra/helm/queenswood
    helm upgrade --install queenswood infra/helm/queenswood \
      -n queenswood --create-namespace \
      --set image.tag={{ tag }} \
      --set image.registry={{ registry }} \
      --set secrets.adminApiKey=$key \
      --wait --timeout 10m

# Create kind cluster, push images to local registry, and install the chart
kind-up tag="dev":
    #!/usr/bin/env zsh
    # Uses a host-side registry to deduplicate layers across rebuilds — dramatically
    # faster than `kind load docker-image` for iterative rebuilds.
    set -e
    bash {{ justfile_directory() }}/infra/kind/with-registry.sh queenswood
    just docker-build-all {{ tag }}
    for project in projects/*-service/; do
      svc=${project:t}
      docker tag {{ DOCKER_REGISTRY }}/$svc:{{ tag }} localhost:5001/$svc:{{ tag }}
      docker push localhost:5001/$svc:{{ tag }}
    done
    just helm-install {{ tag }} "" localhost:5001

# Tear down the kind cluster
kind-down:
    kind delete cluster --name queenswood

# Create the xp-mp kind cluster and install Crossplane/ArgoCD into crossplane-system
kind-xp-up:
    #!/usr/bin/env zsh
    set -e
    kind get clusters | grep -q '^{{ XP_CLUSTER }}$' || kind create cluster --name '{{ XP_CLUSTER }}'

    helm repo add crossplane-stable https://charts.crossplane.io/stable \
      --kube-context {{ KIND_XP_CLUSTER }}
    helm repo update --kube-context {{ KIND_XP_CLUSTER }}
    helm upgrade --install crossplane crossplane-stable/crossplane \
      --kube-context {{ KIND_XP_CLUSTER }} \
      --namespace crossplane-system --create-namespace

    kubectl --context {{ KIND_XP_CLUSTER }} create namespace argocd \
      --dry-run=client -o yaml | kubectl --context {{ KIND_XP_CLUSTER }} apply -f -
    kubectl --context {{ KIND_XP_CLUSTER }} apply --server-side -n argocd \
      -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
    kubectl --context {{ KIND_XP_CLUSTER }} -n argocd wait \
      --for=condition=available --timeout=300s deployment/argocd-server
    kubectl --context {{ KIND_XP_CLUSTER }} -n argocd get secret argocd-initial-admin-secret \
      -o jsonpath='{.data.password}' 2>/dev/null | base64 -d || true

kind-xp-down:
    kind delete cluster --name {{ XP_CLUSTER }}

kind-xp-install-gcp-secret:
    kubectl --context {{ KIND_XP_CLUSTER }} create secret generic gcp-creds \
      -n crossplane-system \
      --from-file=creds=$HOME/.config/gcloud/application_default_credentials.json \
      --dry-run=client -o yaml | kubectl --context {{ KIND_XP_CLUSTER }} apply -f -

# Set per-cluster project-scoped fields that Argo manages with PLACEHOLDER
# in git: ProviderConfig.spec.projectID and Cluster.workloadPool. Both have
# `ignoreDifferences` rules so selfHeal won't revert these patches.
kind-xp-install-gcp-config:
    #!/usr/bin/env zsh
    set -e
    : "${GOOGLE_CLOUD_PROJECT_ID:?must be set}"
    until kubectl --context {{ KIND_XP_CLUSTER }} get \
      providerconfig.gcp.upbound.io default >/dev/null 2>&1; do sleep 5; done
    kubectl --context {{ KIND_XP_CLUSTER }} patch providerconfig.gcp.upbound.io \
      default --type=merge \
      -p "{\"spec\":{\"projectID\":\"$GOOGLE_CLOUD_PROJECT_ID\"}}"
    until kubectl --context {{ KIND_XP_CLUSTER }} get \
<<<<<<< queenswood-crossplane-v2-composition
      clusterproviderconfig.gcp.m.upbound.io default >/dev/null 2>&1; do sleep 5; done
    kubectl --context {{ KIND_XP_CLUSTER }} patch clusterproviderconfig.gcp.m.upbound.io \
      default --type=merge \
      -p "{\"spec\":{\"projectID\":\"$GOOGLE_CLOUD_PROJECT_ID\"}}"
    until kubectl --context {{ KIND_XP_CLUSTER }} get \
=======
>>>>>>> main
      cluster.container.gcp.upbound.io queenswood-gke >/dev/null 2>&1; do sleep 5; done
    kubectl --context {{ KIND_XP_CLUSTER }} patch cluster.container.gcp.upbound.io \
      queenswood-gke --type=merge \
      -p "{\"spec\":{\"forProvider\":{\"workloadIdentityConfig\":{\"workloadPool\":\"$GOOGLE_CLOUD_PROJECT_ID.svc.id.goog\"}}}}"

# Start Tilt dev loop (creates kind cluster if missing)
tilt-up:
    #!/usr/bin/env zsh
    # Refreshes subchart deps once before handing off so Tilt's helm() watcher
    # doesn't see Chart.lock / charts/*.tgz appear mid-session and loop.
    set -e
    kind get clusters | grep -q queenswood || kind create cluster --name queenswood
    helm dependency update infra/helm/queenswood
    tilt up

# Tear down Tilt-managed resources (leaves kind cluster intact)
tilt-down:
    tilt down

# Prune orphaned Tilt images from Docker daemon and kind node containerd
tilt-prune:
    #!/usr/bin/env zsh
    # Tilt rewrites every rebuild's manifest to a fresh `:tilt-<hex>` tag; per-edit
    # rebuilds accumulate orphan images on the host and inside kind. Safe vs `:dev`
    # and version tags — the glob only matches `*:tilt-*`.
    set -e
    images=$(docker images --filter 'reference=ghcr.io/kjothen/*:tilt-*' -q | sort -u)
    if [[ -n "$images" ]]; then
      echo "$images" | xargs docker rmi -f
    else
      echo "No host-side Tilt images to prune."
    fi
    if docker ps --format '{{ "{{" }}.Names{{ "}}" }}' | grep -q '^queenswood-control-plane$'; then
      docker exec queenswood-control-plane crictl rmi --prune || true
    else
      echo "kind node 'queenswood-control-plane' not running — skipping containerd prune."
    fi

# Print the cluster admin API key from the queenswood Secret
kube-admin-key:
    #!/usr/bin/env zsh
    # Designed for command substitution: VITE_MONO_ADMIN_API_KEY=$(just kube-admin-key) just bank-app-start
    set -euo pipefail
    key=$(kubectl -n queenswood get secret queenswood-admin-api-key \
      -o jsonpath='{.data.MONO_ADMIN_API_KEY}' | base64 -d)
    if [[ -z "$key" ]]; then
      echo "queenswood-admin-api-key Secret missing or empty in namespace queenswood — is the cluster up and the chart installed?" >&2
      exit 1
    fi
    printf '%s' "$key"
