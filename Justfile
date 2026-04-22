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


# Run all polylith project tests
test: start-docker
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
start-docker:
    colima status 2>/dev/null || colima start --arch aarch64 --vm-type vz --vz-rosetta --cpu 6 --memory 12 
    docker context use colima

# Stop Docker via Colima
stop-docker:
    colima stop

start-bank-app:
    cd {{ justfile_directory() }}/bases/bank-app && npm install && npm run dev

start-telemetry:
  docker run -d --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/jaeger:latest \
    --set receivers.otlp.protocols.http.endpoint=0.0.0.0:4318

stop-telemetry:
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
    ADMIN_TOKEN="$MONO_ADMIN_API_KEY" \
    ORG_TOKEN="$org_token" \
    PYTHONPATH="{{ justfile_directory() }}/scripts" \
    SCHEMATHESIS_HOOKS=schemathesis_hooks \
      uvx schemathesis run "{{ base_url }}/openapi.json" \
        --output-sanitize false \
        --max-examples 500 \
        --continue-on-failure

