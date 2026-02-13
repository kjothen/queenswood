set shell := ["zsh", "-cu"]

HOME := env_var('HOME')

# Colima/Docker configuration for testcontainers
export DOCKER_HOST := "unix://" + HOME + "/.config/colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE := "/var/run/docker.sock"

list:
    just --list

# Start nREPL server for Conjure connection
repl:
    clj -M:dev:test:nrepl

# Start Rebel Readline REPL with colors and completion
rebel:
    clj -M:dev:test:rebel

# Start polylith shell
shell:
    clj -M:poly shell

# Build all polylith projects as uberjars
build snapshot="true":
    cd projects/iam && clojure -X:build uber :snapshot {{ snapshot }}
    cd projects/symmetric-key-vault && clojure -X:build uber :snapshot {{ snapshot }}
    cd projects/message-reader && clojure -X:build uber :snapshot {{ snapshot }}


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

# Linter
lint-eastwood:
    clojure -M:dev:test:lint/eastwood
lint-clj-kondo:
    clojure -M:lint/clj-kondo --lint bases components development
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
        echo "$files" | xargs clojure -M:format/zprint -w
        echo "✓ Formatting complete"
    else
        echo "No Clojure files found"
    fi

# Start Docker via Colima
start-docker:
    colima status 2>/dev/null || colima start --cpu 2 --memory 4
    docker context use colima

# Stop Docker via Colima
stop-docker:
    colima stop
