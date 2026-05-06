# Start the Vite dev server for bank-app (set VITE_MONO_ADMIN_API_KEY in env)
bank-app-start:
    # To use the kube-installed bank-api: VITE_MONO_ADMIN_API_KEY=$(just kube-admin-key) just bank-app-start
    # Setting VITE_* inside the recipe body doesn't reliably reach Vite — pass it on the parent shell instead.
    cd {{ justfile_directory() }}/bases/bank-app && \
      npm install && npm run dev

# Run the bank-monolith against its test application.yml, teeing stdout/stderr to server.log
bank-monolith-start:
    clj -M{{ DOMAIN_ALIASES }}:dev:test -e "(require 'com.repldriven.mono.testcontainers.interface)" -m com.repldriven.mono.bank-monolith.main -c "classpath:bank-monolith/application-test.yml" -p "dev" 2>&1 | tee server.log
