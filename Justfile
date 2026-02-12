set shell := ["zsh", "-cu"]

HOME := env_var('HOME')
ZSHENV := env_var('HOME') + '/.zshenv'
XDG_CONFIG_HOME := env_var_or_default('XDG_CONFIG_HOME', env_var('HOME') + '/.config')

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
    # cd components/event && clojure -X:build avro
    cd projects/iam && clojure -X:build uber :snapshot {{ snapshot }}
    cd projects/symmetric-key-vault && clojure -X:build uber :snapshot {{ snapshot }}
    cd projects/message-reader && clojure -X:build uber :snapshot {{ snapshot }}


# Run all polylith project tests
test: start-docker
    SKIP_META=repl clojure -M:poly test :all

# Linter
lint-eastwood:
    clojure -M:dev:test:lint/eastwood
lint-clj-kondo:
    for dir in `find . -type d -name 'src' -or -name 'test'`; do clj -M:lint/clj-kondo --lint $dir; done
lint:
  just lint-eastwood
  just lint-clj-kondo

# Formatter - uses .zprint.edn config in project root
format:
    git ls-files '*.clj' '*.cljc' '*.cljs' '*.edn' | xargs -I {} clojure -M:format/zprint -w {}

# Install
install:
    brew bundle install --file={{justfile_directory()}}/Brewfile
    just configure-clojure

# Start Docker via Colima
start-docker:
    colima status 2>/dev/null || colima start
    docker context use colima

# Stop Docker via Colima
stop-docker:
    colima stop
