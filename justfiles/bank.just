# Start the Vite dev server for the bank-app operator console (port 5174)
bank-app-start:
    #!/usr/bin/env zsh
    # Sign in as `ops` / `ops` (seeded in the queenswood-ops realm).
    # Pair with `just bank-monolith-start` (or `just keycloak-up`)
    # so the SPA can reach Keycloak on localhost:8090 and bank-api
    # on localhost:8080.
    set -e
    cd {{ justfile_directory() }}
    [[ -f bases/bank-app/.env.local ]] || cp bases/bank-app/.env.example bases/bank-app/.env.local
    echo "bank-app: sign in as 'ops' / 'ops' once Keycloak is reachable."
    # Yarn 4 workspace: a single install at the repo root hoists deps
    # for bank-app + bank-console + the shared @queenswood/bank-ui
    # design brick. Corepack resolves yarn 4 from the `packageManager`
    # field in root package.json, so no global yarn install is needed.
    corepack yarn install
    corepack yarn workspace bank-app dev

# Start the Vite dev server for the bank-console org-user SPA (port 5173)
bank-console-start:
    #!/usr/bin/env zsh
    # Sign in as `dev` / `dev` (seeded in the queenswood realm).
    # Pair with `just bank-monolith-start` (or `just keycloak-up`)
    # so the SPA can reach Keycloak on localhost:8090 and bank-api
    # on localhost:8080.
    set -e
    cd {{ justfile_directory() }}
    [[ -f bases/bank-console/.env.local ]] || cp bases/bank-console/.env.example bases/bank-console/.env.local
    echo "bank-console: sign in as 'dev' / 'dev' once Keycloak is reachable."
    corepack yarn install
    corepack yarn workspace bank-console dev

# Start the @queenswood/bank-ui showcase (port 5173, or next free)
bank-ui-showcase:
    #!/usr/bin/env zsh
    # A self-mounting living spec of the design system: every section
    # in showcase/Showcase.svelte renders the real exported component
    # from @queenswood/bank-ui, so the spec can't drift from the
    # implementation.
    set -e
    cd {{ justfile_directory() }}
    echo "bank-ui showcase: every section mounts the real exported component."
    corepack yarn install
    corepack yarn workspace @queenswood/bank-ui showcase

# Run the bank-monolith against its test application.yml, teeing stdout/stderr to server.log
bank-monolith-start:
    clj -M{{ DOMAIN_ALIASES }}:dev:test -e "(require 'com.repldriven.mono.testcontainers.interface)" -m com.repldriven.mono.bank-monolith.main -c "classpath:bank-monolith/application-test.yml" -p "dev" 2>&1 | tee server.log
