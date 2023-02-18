set shell := ["zsh", "-cu"]

HOME := env_var('HOME')
ZSHENV := env_var('HOME') + '/.zshenv'
XDG_CONFIG_HOME := env_var_or_default('XDG_CONFIG_HOME', env_var('HOME') + '/.config')

list:
    just --list

# Config clojure and related tools
configure-clojure:
    rm -rf '{{XDG_CONFIG_HOME}}/clojure'
    git clone git@github.com:practicalli/clojure-deps-edn.git '{{XDG_CONFIG_HOME}}/clojure'

    grep 'export XDG_CONFIG_HOME' '{{ZSHENV}}' \
      || echo 'export XDG_CONFIG_HOME={{XDG_CONFIG_HOME}}' >> '{{ZSHENV}}'
    clojure -Sdescribe

    echo '{:search-config? true}' >> '{{HOME}}/.zprintrc'

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
test:
    clojure -M:poly test :all

# Linter
lint-eastwood:
    clojure -M:dev:test:lint/eastwood
lint-clj-kondo:
    for dir in `find . -type d -name 'src' -or -name 'test'`; do clj -M:lint/clj-kondo --lint $dir; done
lint:
  just lint-eastwood
  just lint-clj-kondo

# Formatter
format:
    # for file in `git ls-files -z '*.edn' '*.clj'`; do clj -M:format/zprint -w $file; done
    if (( $+commands[zprint] )); then git ls-files -z '*.edn' '*.clj' | xargs -0 -I '{}' sh -c "zprint '{:search-config? true}' -w {}"; fi

# Install
install:
    brew bundle install --file={{justfile_directory()}}/Brewfile
    just configure-clojure
