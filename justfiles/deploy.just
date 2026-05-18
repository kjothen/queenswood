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
helm-install tag="dev" admin_key="" registry="ghcr.io/repldriven":
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

# Create kind cluster and install the chart from ghcr.io/repldriven (no local build).
kind-up-remote tag="latest":
    #!/usr/bin/env zsh
    # For the iterative build-test loop, use `kind-up` instead. This
    # path skips the host-side registry + docker buildx; containerd
    # pulls images directly from ghcr on first pod schedule.
    set -e
    kind get clusters | grep -q '^queenswood$' || kind create cluster --name queenswood
    just helm-install {{ tag }} "" ghcr.io/repldriven

# Tear down the kind cluster
kind-down:
    kind delete cluster --name queenswood

# Create the xp-mp kind cluster and install Crossplane/ArgoCD into crossplane-system
kind-xp-bootstrap:
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
    # 600s (not 300s): on a cold kind cluster the argo install pulls
    # ~5 images sequentially (argocd-server, repo-server, application-
    # controller, applicationset-controller, notifications-controller,
    # redis, dex), and even a fast laptop can blow past 5 min on the
    # first gcp-up of the day.
    kubectl --context {{ KIND_XP_CLUSTER }} -n argocd wait \
      --for=condition=available --timeout=600s deployment/argocd-server

    # Teach Argo CD to grade upbound Crossplane MRs by walking their
    # Ready condition (otherwise Argo defaults the kinds to Healthy
    # before Crossplane's controller has even observed them).
    kubectl --context {{ KIND_XP_CLUSTER }} apply --server-side --force-conflicts \
      -f infra/argocd/health-customizations.yaml

    kubectl --context {{ KIND_XP_CLUSTER }} -n argocd get secret argocd-initial-admin-secret \
      -o jsonpath='{.data.password}' 2>/dev/null | base64 -d || true

kind-xp-down:
    kind delete cluster --name {{ XP_CLUSTER }}

kind-xp-install-gcp-secret:
    kubectl --context {{ KIND_XP_CLUSTER }} create secret generic gcp-creds \
      -n crossplane-system \
      --from-file=creds=$HOME/.config/gcloud/application_default_credentials.json \
      --dry-run=client -o yaml | kubectl --context {{ KIND_XP_CLUSTER }} apply -f -

# Stand up the kind management plane end-to-end (cluster, GCP creds, root app, wait healthy).
kind-xp-up:
    #!/usr/bin/env zsh
    set -e
    just kind-xp-bootstrap
    just kind-xp-install-gcp-secret
    just kind-xp-install-root
    just kind-xp-wait-ready

# Render the root Argo Application with the live PROJECT_ID baked
# into its `helm.parameters.gcpProjectId`. From there the bootstrap
# chart fans out into the wave-ordered child Applications --
# crossplane-providers (0), crossplane-configs + crossplane-xrds +
# queenswood-gcp (1), queenswood-platform (4) -- so queenswood-platform
# isn't created until queenswood-gcp is Healthy (GKE cluster Ready,
# kubeconfig secret written).
kind-xp-install-root:
    #!/usr/bin/env zsh
    set -e
    export PROJECT_ID=$(just _gcp-project-id)
    envsubst < infra/bootstrap/root-app.yml.tmpl \
      | kubectl --context {{ KIND_XP_CLUSTER }} apply -f -

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
    images=$(docker images --filter 'reference=ghcr.io/repldriven/*:tilt-*' -q | sort -u)
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

# Print Keycloak admin credentials + console URL for the active env
keycloak-admin-login:
    #!/usr/bin/env zsh
    set -euo pipefail
    KCTX=$(just _gke-ctx)
    user=$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get secret queenswood-keycloak \
      -o jsonpath='{.data.admin-user}' 2>/dev/null | base64 -d)
    pass=$(kubectl --context $KCTX -n {{ QUEENSWOOD_ENV }} get secret queenswood-keycloak \
      -o jsonpath='{.data.admin-password}' 2>/dev/null | base64 -d)
    if [[ -z "$user" || -z "$pass" ]]; then
      echo "queenswood-keycloak Secret missing or empty in namespace {{ QUEENSWOOD_ENV }} — is the Keycloak chart installed?" >&2
      exit 1
    fi
    echo "Admin console: https://keycloak.{{ QUEENSWOOD_DOMAIN }}/admin/"
    echo "User:          $user"
    echo "Password:      $pass"
