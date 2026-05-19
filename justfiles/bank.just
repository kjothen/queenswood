# Start the Vite dev server for the bank-app operator console (port 5174)
bank-app-start:
    #!/usr/bin/env zsh
    # Sign in as `ops` / `ops` (seeded in the queenswood-ops realm).
    # Pair with `just bank-monolith-start` (or `just keycloak-up`)
    # so the SPA can reach Keycloak on localhost:8090 and bank-api
    # on localhost:8080.
    set -e
    cd {{ justfile_directory() }}/bases/bank-app
    [[ -f .env.local ]] || cp .env.example .env.local
    echo "bank-app: sign in as 'ops' / 'ops' once Keycloak is reachable."
    npm install
    npm run dev

# Start the Vite dev server for the bank-console org-user SPA (port 5173)
bank-console-start:
    #!/usr/bin/env zsh
    # Sign in as `dev` / `dev` (seeded in the queenswood realm).
    # Pair with `just bank-monolith-start` (or `just keycloak-up`)
    # so the SPA can reach Keycloak on localhost:8090 and bank-api
    # on localhost:8080.
    set -e
    cd {{ justfile_directory() }}/bases/bank-console
    [[ -f .env.local ]] || cp .env.example .env.local
    echo "bank-console: sign in as 'dev' / 'dev' once Keycloak is reachable."
    npm install
    npm run dev

# Run the bank-monolith against its test application.yml, teeing stdout/stderr to server.log
bank-monolith-start:
    clj -M{{ DOMAIN_ALIASES }}:dev:test -e "(require 'com.repldriven.mono.testcontainers.interface)" -m com.repldriven.mono.bank-monolith.main -c "classpath:bank-monolith/application-test.yml" -p "dev" 2>&1 | tee server.log
