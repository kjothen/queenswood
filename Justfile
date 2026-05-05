set shell := ["zsh", "-cu"]

DOMAIN_ALIASES := ":+bank"

list:
    just --list

# Sync upstream changes
sync-upstream:
  chmod +x {{ justfile_directory() }}/scripts/sync-upstream.sh
  {{ justfile_directory() }}/scripts/sync-upstream.sh

# Remove the exemplar and configure for a new domain
fork domain:
    bb scripts/fork-domain.bb {{ domain }}

# Start nREPL server for Conjure connection
repl:
    find . -name .nrepl-port -not -path ./.nrepl-port -delete
    clojure -M{{ DOMAIN_ALIASES }}:dev:test:nrepl -Sforce -P

# Start Rebel Readline REPL with colors and completion
rebel:
    clj -M{{ DOMAIN_ALIASES }}:dev:test:rebel

# Start polylith shell
shell:
    clj -M:poly shell

# Build all polylith projects as uberjars
build snapshot="true":
    #!/usr/bin/env zsh
    for project in projects/*/; do
        echo "Building ${project:t}..."
        (cd "$project" && clojure -X:build uber :snapshot {{ snapshot }})
    done

# Container registry for Queenswood service images
DOCKER_REGISTRY := "ghcr.io/kjothen"

# Build a single service Docker image: `just docker-build bank-api-service dev`
docker-build project tag="dev":
    docker buildx build \
      --build-arg PROJECT_NAME={{ project }} \
      -t {{ DOCKER_REGISTRY }}/{{ project }}:{{ tag }} \
      -f infra/docker/service/Dockerfile \
      --load \
      .

# Build every Queenswood service image in parallel via
# `docker buildx bake`. Targets are declared in
# infra/docker/bake.hcl; the Clojure base layer is shared
# across all targets, so this is dramatically faster than
# the per-image loop.
docker-build-all tag="dev":
    REGISTRY={{ DOCKER_REGISTRY }} TAG={{ tag }} \
      docker buildx bake -f infra/docker/bake.hcl

# Render the Helm chart locally (does not install)
helm-template tag="dev":
    helm dependency update infra/helm/queenswood
    helm template bank infra/helm/queenswood \
      --set image.tag={{ tag }} \
      --set secrets.adminApiKey=template-render

# Install/upgrade the chart into the current kubectl context.
# Reuses the existing Secret value on subsequent installs so the
# admin API key is stable across deploys; pass `admin_key=...` to
# rotate. A fresh key is generated only when no Secret exists yet.
helm-install tag="dev" admin_key="":
    #!/usr/bin/env zsh
    set -e
    key={{ admin_key }}
    if [[ -z "$key" ]]; then
      key=$(kubectl get secret bank-admin-api-key \
        -o jsonpath='{.data.MONO_ADMIN_API_KEY}' 2>/dev/null \
        | base64 -d || true)
      [[ -z "$key" ]] && key=$(openssl rand -hex 16)
    fi
    helm dependency update infra/helm/queenswood
    helm upgrade --install bank infra/helm/queenswood \
      --set image.tag={{ tag }} \
      --set secrets.adminApiKey=$key \
      --wait --timeout 10m

# Spin up a kind cluster, load all images, install the chart end-to-end
kind-up tag="dev":
    #!/usr/bin/env zsh
    set -e
    kind get clusters | grep -q queenswood || kind create cluster --name queenswood
    just docker-build-all {{ tag }}
    for project in projects/*-service/; do
      svc=${project:t}
      kind load docker-image --name queenswood {{ DOCKER_REGISTRY }}/$svc:{{ tag }}
    done
    just helm-install {{ tag }}

# Tear down the kind cluster
kind-down:
    kind delete cluster --name queenswood

# Create the kind cluster if missing, refresh the chart's subchart
# deps once (so Tilt's helm() watcher doesn't see Chart.lock /
# charts/*.tgz appearing mid-session and loop forever), then hand
# off to Tilt for the interactive dev loop. Prereq: `just start-docker`.
tilt-up:
    #!/usr/bin/env zsh
    set -e
    kind get clusters | grep -q queenswood || kind create cluster --name queenswood
    helm dependency update infra/helm/queenswood
    tilt up

# Tear down Tilt-managed resources. Leaves the kind cluster in
# place — use `just kind-down` if you also want to nuke it.
tilt-down:
    tilt down

# Prune all Tilt-built images. Tilt rewrites every rebuild's
# manifest to point at a fresh `:tilt-<hex>` tag, so per-edit
# rebuilds accumulate dozens of orphan images on the host Docker
# daemon and inside the kind node's containerd. This recipe
# nukes both. Safe vs your `:dev` / version tags — the glob only
# matches `*:tilt-*`.
tilt-prune:
    #!/usr/bin/env zsh
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


# Run all polylith project tests
test: docker-start
    SKIP_META=repl clojure -M:poly test :all

# Check test failures from last test run
poly-test-check:
    #!/usr/bin/env python3
    import xml.etree.ElementTree as ET
    import sys
    from pathlib import Path

    xml_file = Path("./target/test-results/junit.xml")

    if not xml_file.exists():
        print("❌ No test results found. Run 'just test' first.")
        sys.exit(1)

    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()

        failures = []
        for testsuite in root.findall('.//testsuite'):
            for testcase in testsuite.findall('.//testcase'):
                failure = testcase.find('failure')
                if failure is not None:
                    failures.append({
                        'package': testsuite.get('package', ''),
                        'test': testcase.get('name', ''),
                        'class': testcase.get('classname', ''),
                        'message': failure.text or ''
                    })

        if failures:
            print("\n=== Failed Tests ===\n")
            for f in failures:
                print(f"❌ {f['package']}/{f['test']}")
                print(f"   {f['message'].strip()[:200]}")
                print()
            print(f"Total failures: {len(failures)}")
        else:
            print("✅ All tests passed!")

    except Exception as e:
        print(f"Error reading test results: {e}")
        sys.exit(1)

# Check dependencies for known CVEs (no args = dev classpath, or pass project name)
nvd project="":
    #!/usr/bin/env zsh
    if [[ -z "{{ project }}" ]]; then
      classpath=$(clojure -Spath -A{{ DOMAIN_ALIASES }}:dev)
    else
      classpath=$(cd projects/{{ project }} && clojure -Spath)
    fi
    clojure -J-Dclojure.main.report=stderr -J-Danalyzer.ossindex.enabled=false -M:nvd "nvd-clojure.edn" "$classpath"

# Linter
lint-eastwood:
    clojure -M{{ DOMAIN_ALIASES }}:dev:test:lint/eastwood
lint-clj-kondo:
    clojure -M:lint/clj-kondo --lint bases components projects deps.edn workspace.edn
lint:
  just lint-eastwood
  just lint-clj-kondo

# Formatter - uses .zprint.edn config in project root
format:
    #!/usr/bin/env bash
    set -e
    echo "Formatting Clojure source files..."
    files=$(git ls-files '*.clj' '*.cljc' '*.cljs' | while read f; do [ -f "$f" ] && echo "$f"; done)
    if [ -n "$files" ]; then
        echo "$files" | xargs clojure -M:format/zprint '{:search-config? true}' -w
        echo "✓ Formatting complete"
    else
        echo "No Clojure files found"
    fi

export-openapi path="docs/openapi.yaml":
    clojure -Sdeps '{:aliases {:dev {:main-opts []}}}' -M{{ DOMAIN_ALIASES }}:dev -m com.repldriven.mono.bank-api.export-spec {{ path }}

force-prep:
    clj -X:deps prep :aliases '[{{ DOMAIN_ALIASES }} :dev]' :force true

# Start Docker via Colima
docker-start:
    colima status 2>/dev/null || colima start --arch aarch64 --vm-type vz --vz-rosetta --cpu 6 --memory 24 --disk 100
    # Raise inotify limits — kind nodes plus per-pod kubectl/curl
    # polling exhaust the defaults (128 instances, 8192 watches)
    # and surface as "too many open files" in init containers.
    colima ssh -- sudo sysctl -w fs.inotify.max_user_instances=8192 fs.inotify.max_user_watches=524288
    docker context use colima
    docker system prune -af --volumes

# Stop Docker via Colima
docker-stop:
    colima stop

# Install bank-app deps and run the Vite dev server against the local bank-api.
# With use-kube-secret=true, fetch MONO_ADMIN_API_KEY from the
# `bank-admin-api-key` Secret in the current kube context and expose it
# to Vite via VITE_MONO_ADMIN_API_KEY (default: rely on a local .env or
# whatever the shell already has).
bank-app-start use-kube-secret="false":
    #!/usr/bin/env zsh
    set -euo pipefail
    cd {{ justfile_directory() }}/bases/bank-app
    if [[ "{{ use-kube-secret }}" == "true" ]]; then
      VITE_MONO_ADMIN_API_KEY=$(kubectl get secret bank-admin-api-key \
        -o jsonpath='{.data.MONO_ADMIN_API_KEY}' | base64 -d)
      if [[ -z "$VITE_MONO_ADMIN_API_KEY" ]]; then
        echo "kubectl returned empty MONO_ADMIN_API_KEY — is the cluster up and the chart installed?" >&2
        exit 1
      fi
      export VITE_MONO_ADMIN_API_KEY
    fi
    npm install && npm run dev

# Run the bank-monolith against its test application.yml, teeing stdout/stderr to server.log
bank-monolith-start:
    clj -M{{ DOMAIN_ALIASES }}:dev:test -e "(require 'com.repldriven.mono.testcontainers.interface)" -m com.repldriven.mono.bank-monolith.main -c "classpath:bank-monolith/application-test.yml" -p "dev" 2>&1 | tee server.log

# Start a local Jaeger container for OTLP traces (UI on :16686, OTLP/http on :4318)
telemetry-start:
  docker run -d --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/jaeger:latest \
    --set receivers.otlp.protocols.http.endpoint=0.0.0.0:4318

# Stop and remove the local Jaeger container
telemetry-stop:
  docker stop jaeger && docker rm jaeger

# Run schemathesis API fuzzer against the running bank-api
schemathesis base_url="http://127.0.0.1:8080":
    #!/usr/bin/env zsh
    set -e
    echo "Picking a tier..."
    tier_id=$(curl -s -H "Authorization: Bearer $MONO_ADMIN_API_KEY" \
      "{{ base_url }}/v1/tiers" | jq -r '[.tiers[] | select(.name == "Standard")][0]["tier-id"]')
    if [[ -z "$tier_id" || "$tier_id" == "null" ]]; then
      echo "Failed to resolve Standard tier-id from /v1/tiers"
      exit 1
    fi
    echo "Creating test organization on tier $tier_id..."
    response=$(curl -s -X POST "{{ base_url }}/v1/organizations" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $MONO_ADMIN_API_KEY" \
      -d '{"name": "Schemathesis Test Org", "status": "test", "tier-id": "'"$tier_id"'", "currencies": ["GBP"]}')
    org_token=$(echo "$response" | jq -r '.["api-key-secret"]')
    if [[ -z "$org_token" || "$org_token" == "null" ]]; then
      echo "Failed to create organization: $response"
      exit 1
    fi
    echo "Running schemathesis..."
    # `stateful` is excluded because schemathesis's LinkInferencer
    # builds a werkzeug routing Map from the spec's paths, and werkzeug
    # rejects hyphens in <name> path converters — our `{account-id}` /
    # `{product-id}` templates trip it. The explicit `:links` we emit
    # still render in Scalar for docs / humans; we just skip link-driven
    # stateful test generation until either schemathesis tolerates
    # hyphenated names or we rename path params.
    ADMIN_TOKEN="$MONO_ADMIN_API_KEY" \
    ORG_TOKEN="$org_token" \
    PYTHONPATH="{{ justfile_directory() }}/scripts" \
    SCHEMATHESIS_HOOKS=schemathesis_hooks \
      uvx schemathesis run "{{ base_url }}/openapi.json" \
        --output-sanitize false \
        --max-examples 500 \
        --phases=examples,coverage,fuzzing \
        --continue-on-failure

