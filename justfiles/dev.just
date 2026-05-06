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

# Run Eastwood static analysis
lint-eastwood:
    clojure -M{{ DOMAIN_ALIASES }}:dev:test:lint/eastwood

# Run clj-kondo linter across all bricks and config files
lint-clj-kondo:
    clojure -M:lint/clj-kondo --lint bases components projects deps.edn workspace.edn

# Run all linters (Eastwood + clj-kondo)
lint:
  just lint-eastwood
  just lint-clj-kondo

# Format all Clojure source files with zprint
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
