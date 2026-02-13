# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

This is a Clojure monorepo using the **Polylith** architecture pattern. The codebase is structured as follows:

- **Components** (`components/`): Reusable building blocks with interface/implementation separation
  - Each component has an `interface.clj` that defines the public API
  - Implementation is contained within the component's namespace
  - Key components include: system, log, env, http-client, server, db, migrator, pulsar, avro, mqtt, vault, encryption, iam, error, event
- **Bases** (`bases/`): Application entry points and web APIs
  - `iam-api`: Identity and Access Management API
  - `symmetric-key-api`: Cryptographic key management API  
  - `blocking-command-api`: Command processing API
  - `pulsar-reader`: Message queue consumer
- **Projects** (`projects/`): Deployable applications that combine components and bases
  - `iam`: IAM service combining iam-api base with required components
  - `symmetric-key-vault`: Key management service
  - `message-reader`: Message processing service

The system uses **Donut System** for dependency injection and component lifecycle management, wrapped by the custom `system` component.

## Development Commands

Run these commands from the repository root:

### Core Polylith Commands
```bash
# Start interactive Polylith shell for exploration
just shell

# Run all tests across the entire monorepo
just test

# Build all project uberjars
just build

# Build with development snapshot versions
just build true
```

### Code Quality
```bash
# Run all linters
just lint

# Format all Clojure code
just format

# Individual linters
just lint-eastwood    # Static analysis
just lint-clj-kondo   # Linting
```

## Testing

- Individual component tests can be run with: `clojure -M:test`
- All tests run via: `clojure -M:poly test :all`
- Test resources are located in `test-resources/` directories within each component/base
- The codebase uses test.check for property-based testing where applicable

## Key Architectural Patterns

### Component Structure
Each component follows this pattern:
```
component-name/
├── deps.edn           # Component dependencies
├── src/com/repldriven/mono/component_name/
│   ├── interface.clj  # Public API
│   └── [impl files]   # Implementation
└── test/             # Tests
```

### System Configuration
The system configuration is the key architectural pattern - entire systems are defined as data in YAML (preferred) or EDN files:
- System definitions specify all components, their dependencies, and configuration
- The `env` component handles loading configuration:
  - `-c` flag for config file path
  - `-p` flag for profile selection (dev, test, prod)
- Environment-specific profiles allow different configurations for dev, test, and prod
- **Testcontainers Integration**: Substantial support for spinning up test infrastructure (databases, message queues, etc.) defined in the system configuration
- **Interceptor-based Dependency Injection**:
  - Servers are configured with interceptors in the system definition
  - Interceptors provide handlers with access to system components they require
  - Handlers can access datasources, connections (MQTT, Pulsar), and other components via the request context
  - This eliminates the need for global state or manual dependency wiring

### Error Handling
The codebase uses anomaly-based error handling via the `error` component (based on nom library):
- Most interface functions return either a value or an anomaly (not exceptions)
- Anomalies are maps with a `:category` key indicating the error kind
- Common error handling functions:
  - `error/anomaly?` - checks if a value is an anomaly
  - `error/kind` - extracts the error category/kind from an anomaly
  - `error/fail` - creates a new anomaly with a kind and message
  - `error/let-nom` - monadic let that short-circuits on anomalies (similar to `let` but stops on first error)
  - `error/nom->` - threading macro that short-circuits on anomalies
- When calling interface functions, always check for anomalies before using the result
- Prefer `error/let-nom` over nested if-checks when chaining operations that may fail

### Database Integration
- PostgreSQL integration via `db` component
- Database migrations handled by `migrator` component using Liquibase
- Connection pooling and lifecycle managed by the system component

### Message Queue Integration
- Apache Pulsar integration via `pulsar` component
- Supports encrypted messaging with RSA key pairs
- Topic and schema management built-in
- Uses channel-based async patterns (core.async) for consumers and readers
  - Both `receive` (consumer) and `read` (reader) return `{:c chan :stop chan}`
  - Messages on `:c` channel have format `{:message <Message> :data <deserialized-data>}`
  - Send to `:stop` channel to stop receiving/reading
- Encryption context checking for messages that fail decryption
  - Returns anomaly with `:pulsar/message-decrypt` kind when decryption fails
- Avro serialization/deserialization via `avro` component

## Main Entry Points

Applications are started via their main namespaces:
- `com.repldriven.mono.iam-api.main/-main`
- `com.repldriven.mono.symmetric-key-api.main/-main`

Each accepts `-c config-file` and `-p profile` arguments.

## Development Workflow

1. Use `just shell` to start the Polylith REPL for interactive development
2. Load specific services in development via `development/src/dev/local.clj`
3. Run tests with `just test` before committing
4. Format code with `just format`
5. Build projects with `just build` for deployment