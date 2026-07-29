# Start the Vite dev server for the console org-user SPA (port 5173)
console-start:
    #!/usr/bin/env zsh
    # Sign in as `dev` / `dev` (seeded in the queenswood realm).
    # Pair with `just monolith-start` (or `just keycloak-up`)
    # so the SPA can reach Keycloak on localhost:8090 and api
    # on localhost:8080.
    set -e
    cd {{ justfile_directory() }}
    [[ -f bases/console/.env.local ]] || cp bases/console/.env.example bases/console/.env.local
    echo "console: sign in as 'dev' / 'dev' once Keycloak is reachable."
    corepack yarn install
    corepack yarn workspace console dev

# Start the @queenswood/ui showcase (port 5173, or next free)
ui-showcase:
    #!/usr/bin/env zsh
    # A self-mounting living spec of the design system: every section
    # in showcase/Showcase.svelte renders the real exported component
    # from @queenswood/ui, so the spec can't drift from the
    # implementation.
    set -e
    cd {{ justfile_directory() }}
    echo "ui showcase: every section mounts the real exported component."
    corepack yarn install
    corepack yarn workspace @queenswood/ui showcase

# Run the monolith against its test application.yml, teeing stdout/stderr to server.log
monolith-start:
    clj -M{{ DOMAIN_ALIASES }}:dev:test -e "(require 'com.repldriven.queenswood.testcontainers.interface)" -m com.repldriven.queenswood.monolith.main -c "classpath:monolith/application-test.yml" -p "dev" 2>&1 | tee server.log
