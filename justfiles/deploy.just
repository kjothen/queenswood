# Render the Helm chart locally (does not install)
helm-template tag="dev":
    helm dependency update infra/helm/queenswood
    helm template queenswood infra/helm/queenswood \
      --set image.tag={{ tag }}

# Lint and schema-validate the Helm chart without a cluster
helm-validate tag="dev":
    # CRDs (FoundationDBCluster etc.) are skipped via -ignore-missing-schemas.
    helm dependency update infra/helm/queenswood
    helm lint infra/helm/queenswood
    helm template queenswood infra/helm/queenswood \
      --set image.tag={{ tag }} \
      | kubeconform -strict -ignore-missing-schemas -summary

# Install or upgrade the chart
helm-install tag="dev" registry="ghcr.io/repldriven" values_file="" pull_policy="":
    #!/usr/bin/env zsh
    # `registry` lets `kind-up` point at the local registry so containerd
    # pulls from there. `values_file` layers an extra values file on top
    # of values.yaml -- kind-up passes values-dev.yaml to enable the
    # bundled dev Keycloak. `pull_policy` overrides image.pullPolicy --
    # kind-up sets IfNotPresent so containerd pulls the freshly pushed
    # images from the local registry (values-dev.yaml's Never is for the
    # Tilt flow, which loads images straight into the kind node).
    set -e
    # `values_file` is a space-separated list layered left-to-right
    # (later files win), e.g. "values-dev.yaml values-monolith.yaml".
    extra_values=()
    for vf in {{ values_file }}; do
      extra_values+=(-f infra/helm/queenswood/$vf)
    done
    extra_sets=()
    if [[ -n "{{ pull_policy }}" ]]; then
      extra_sets=(--set image.pullPolicy={{ pull_policy }})
    fi
    helm dependency update infra/helm/queenswood
    helm upgrade --install queenswood infra/helm/queenswood \
      -n queenswood --create-namespace \
      "${extra_values[@]}" \
      --set image.tag={{ tag }} \
      --set image.registry={{ registry }} \
      "${extra_sets[@]}" \
      --wait --timeout 10m

# Create kind cluster, push images to local registry, and install the chart
kind-up tag="dev":
    #!/usr/bin/env zsh
    # Uses a host-side registry to deduplicate layers across rebuilds — dramatically
    # faster than `kind load docker-image` for iterative rebuilds.
    set -e
    bash {{ justfile_directory() }}/infra/kind/with-registry.sh queenswood
    # just docker-build-all {{ tag }}
    # bank-console lives at bases/* (Svelte SPA, not a Polylith service
    # project), so the projects/*-service/ glob misses it.
    # docker-build-all's `default` group in infra/docker/bake.hcl already
    # builds it -- we just need to push it alongside the services.
    for svc in projects/*-service/(:t) bank-console; do
      docker tag {{ DOCKER_REGISTRY }}/$svc:{{ tag }} localhost:5001/$svc:{{ tag }}
      docker push localhost:5001/$svc:{{ tag }}
    done
    just helm-install {{ tag }} localhost:5001 values-dev.yaml IfNotPresent

# Build/push/install only the modular-monolith on kind (channel bus, no Pulsar)
kind-up-monolith tag="dev":
    #!/usr/bin/env zsh
    # One bank-monolith pod over the core.async channel bus. Only the
    # four images the profile needs are built/pushed; values-monolith.yaml
    # disables every other service and the Pulsar subchart, layered over
    # values-dev.yaml so the bundled dev Keycloak stays enabled.
    set -e
    bash {{ justfile_directory() }}/infra/kind/with-registry.sh queenswood
    # The three JVM services share the parameterised service Dockerfile.
    for svc in monolith-service migrator-service bootstrap-service; do
      just docker-build $svc {{ tag }}
    done
    # bank-console has its own Dockerfile; build it via its bake target.
    REGISTRY={{ DOCKER_REGISTRY }} TAG={{ tag }} \
      docker buildx bake -f infra/docker/bake.hcl bank-console
    for svc in monolith-service migrator-service bootstrap-service bank-console; do
      docker tag {{ DOCKER_REGISTRY }}/$svc:{{ tag }} localhost:5001/$svc:{{ tag }}
      docker push localhost:5001/$svc:{{ tag }}
    done
    # Drop the one-shot Jobs so helm recreates them on new code: their
    # name embeds the tag+chart version and the pod spec is immutable, so
    # a plain upgrade skips them and they never re-run. --ignore-not-found
    # + `|| true` keeps the first run (no namespace yet) from tripping set -e.
    kubectl delete job -n queenswood \
      -l 'app.kubernetes.io/component in (migrator,bootstrap)' \
      --ignore-not-found 2>/dev/null || true
    # Always pull so a re-pushed `:{{ tag }}` actually lands on the node —
    # IfNotPresent keeps the stale cached image and silently ignores the
    # new push (the `crictl rmi` dance). The registry is local, so the
    # re-pull is cheap.
    just helm-install {{ tag }} localhost:5001 "values-dev.yaml values-monolith.yaml" Always

# Create kind cluster and install the chart from ghcr.io/repldriven (no local build).
kind-up-remote tag="latest":
    #!/usr/bin/env zsh
    # For the iterative build-test loop, use `kind-up` instead. This
    # path skips the host-side registry + docker buildx; containerd
    # pulls images directly from ghcr on first pod schedule.
    set -e
    kind get clusters | grep -q '^queenswood$' || kind create cluster --name queenswood
    just helm-install {{ tag }} ghcr.io/repldriven

# Tear down the kind cluster
kind-down:
    kind delete cluster --name queenswood

# Run a local dev Keycloak in Docker (foreground; Ctrl+C tears it down)
keycloak-up:
    # Listens on host :8090 to match VITE_KEYCLOAK_URL in
    # bases/bank-console/.env.example. 8090 (not 8081) keeps the
    # port out of the way of bank-monolith, which binds 8081 for
    # the bank-clearbank-simulator dev component. Admin console
    # at http://localhost:8090/admin (admin / admin). Realm JSON
    # imports the queenswood-admin + queenswood-console clients
    # plus the Google IdP (placeholder creds — sign-in via Google
    # only works once you wire real credentials in).
    docker run --rm \
      --name queenswood-keycloak-dev \
      -p 8090:8080 \
      -e KEYCLOAK_ADMIN=admin \
      -e KEYCLOAK_ADMIN_PASSWORD=admin \
      -v "$PWD/infra/helm/queenswood/files/keycloak-realm.json:/opt/keycloak/data/import/realm.json:ro" \
      -v "$PWD/infra/helm/queenswood/files/keycloak-realm-ops.json:/opt/keycloak/data/import/realm-ops.json:ro" \
      quay.io/keycloak/keycloak:26.0 \
      start-dev --import-realm

# Force-stop a stray dev Keycloak container (Ctrl+C is normally enough)
keycloak-down:
    docker rm -f queenswood-keycloak-dev 2>/dev/null || true

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
