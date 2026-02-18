# CLAUDE.md

This file provides guidance to Claude Code when working with this Clojure/Polylith monorepo.

## Polylith Architecture

- **Three artifact types**: Components (reusable), Bases (entry points), Projects (deployable)
- **Components** (`components/`):
  - Reusable building blocks with `interface.clj` defining public API
  - Implementation in same namespace, never access internals directly
  - Examples: system, log, env, http-client, server, db, migrator, pulsar, avro, mqtt, vault, encryption, iam, error, test, event
- **Bases** (`bases/`):
  - Application entry points (e.g., APIs, readers, processors)
  - Have `-main` functions and handle application bootstrap
  - Examples: iam-api, symmetric-key-api, blocking-command-api, pulsar-mqtt-processor
  - Bases cannot depend on other bases
- **Projects** (`projects/`):
  - Combine bases and components into deployable applications
  - No code, just `deps.edn` files
  - Examples: iam, symmetric-key-vault
  - Projects do not have `-main` functions (bases do)

## Component-Based Infrastructure

- **System-as-data**: Entire systems defined in YAML/EDN configuration files
- **Donut System**: Dependency injection and lifecycle management (wrapped by `system` component)
- **Testcontainers**: Test infrastructure (DBs, message queues) defined in system config
- **Interceptor-based DI**:
  - Server interceptors inject component instances into request context
  - Handlers access datasources, MQTT clients, Pulsar consumers via request map
  - No global state or manual dependency wiring
- **Configuration loading** (`env` component):
  - `-c` flag for config file path
  - `-p` flag for profile (dev, test, prod)
  - Supports environment-specific overrides

## Error Handling

- **Anomaly-based** (nom library via `error` component):
  - Functions return values OR anomalies (maps with `:category` key)
  - Never throw exceptions for expected errors
  - `error/anomaly?` - check if value is anomaly
  - `error/kind` - extract error category
  - `error/fail` - create anomaly with kind and message
  - `error/let-nom` - monadic let, short-circuits on first anomaly
  - `error/nom->` - threading macro, short-circuits on anomalies
  - `error/with-anomaly?` - execute operations, call error-fn if any returns anomaly
  - `error/with-let-anomaly?` - execute let-nom bindings, call error-fn if result is anomaly
- **Test assertion**: `test/refute-anomaly` - fails test if value is anomaly

## Testing Patterns

- **Test component**: Provides `test/refute-anomaly` for consistent anomaly checking
- **Testing specific bricks**: When testing a changed component or base (brick), run tests for that brick in the context of a project:

  ```bash
  clojure -M:poly test brick:<brick-name> :project <project-name>
  # Example: clojure -M:poly test brick:accounts :project accounts-processor
  ```

- **with-let-anomaly? pattern**:

  ```clojure
  (error/with-let-anomaly?
    [result1 (operation1)
     _ (is (= expected result1))
     result2 (operation2 result1)]
    test/refute-anomaly)
  ```

- **system/with-system**: Binding-based macro for test system lifecycle

  ```clojure
  (system/with-system [sys (test-system)]
    (let [component (system/instance sys [:path :to :component])]
      ;; test code
      ))
  ```

- **Test resources**: Located in `test-resources/` within each component/base
- **Property-based testing**: Use test.check where applicable
- **Synchronized tests**: Mark with `^:eftest/synchronized` to prevent too many parallel tests from overwhelming CPU/memory

## Database Patterns

- **db component**: PostgreSQL integration
- **migrator component**: Liquibase-based migrations
- **Connection pooling**: Managed by system component lifecycle
- **Testcontainers**: Spin up PostgreSQL in tests via system config

## Message Queue Patterns

- **pulsar component**: Apache Pulsar integration with Avro serialization
- **Channel-based async**: Both `receive` (consumer) and `read` (reader) return `{:c chan :stop chan}`
- **Message format**: `{:message <Message> :data <deserialized-data>}` on `:c` channel
- **Stopping**: Send to `:stop` channel to stop receiving/reading
- **Encryption**: Supports RSA key pairs for encrypted messaging
- **Decryption failures**: Return anomaly with `:pulsar/message-decrypt` kind
- **mqtt component**: MQTT client integration for request-reply patterns

## Development Commands

```bash
just shell          # Start Polylith REPL
just test           # Run all tests
just build          # Build all uberjars
just build true     # Build with snapshot versions
just lint           # Run all linters
just format         # Format all code
just lint-eastwood  # Static analysis
just lint-clj-kondo # Linting
```

## Code Formatting

- **zprint**: All Clojure source is formatted with zprint, configured in `.zprint.edn`
- **Width**: 80 characters
- **Git hook**: `scripts/hooks/pre-commit` automatically formats staged Clojure
  files before each commit. Install it once with:

  ```bash
  cp scripts/hooks/pre-commit .git/hooks/pre-commit
  chmod +x .git/hooks/pre-commit
  ```

- **Docstrings**: zprint does not reflow string content, so docstrings must be
  manually wrapped at 80 characters. Write multi-line docstrings like:

  ```clojure
  (defn my-fn
    "First line of docstring, kept within 80 characters.

    Further detail on subsequent lines, also wrapped at 80 chars. Use
    blank lines to separate paragraphs."
    [args]
    body)
  ```

## Coding Guidelines

- **Referential transparency**: For an expression to be referentially
  transparent, we must be able to bind the expression to a name, substitute
  that name for any or all occurrences of the original expression (within the
  same context), and nothing should have changed (except perhaps the execution
  time). Prefer pure functions that return the same value for the same inputs,
  with no observable side effects. Name functions after what they return, not
  what they do.
- **Wire data uses string keys**: Message data from external systems (Pulsar,
  MQTT, HTTP, JSON) is handled with string keys throughout. Do not convert
  wire data to keyword keys — use `{:strs [...]}` destructuring and string
  literals for map access. Converting between string and keyword keys at
  different layers causes confusion about data provenance.

## Code Linting

- **clj-kondo**: Configured in `.clj-kondo/config.edn` with lint-as mappings for macros
- **Git hook**: `scripts/hooks/pre-commit` also runs clj-kondo against the full
  `bases`, `components`, and `projects` directories when any Clojure files
  are staged, blocking the commit if lint errors are found
