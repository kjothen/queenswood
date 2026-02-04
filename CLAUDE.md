# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Parent Repository Structure

This mono/ directory is part of a multi-project monorepo:

- **mono/** - This Clojure/Polylith codebase (you are here)
- **nix-shells/** - Nix flake providing dev environment (JDK 21, Clojure, clojure-lsp, Babashka, Node.js)
- **advent-of-code/** - Advent of Code solutions
- **prelude/** - Emacs distribution (third-party fork)

The parent directory uses direnv with `nix-shells/flake.nix` for environment management.

## Architecture

This is a Clojure monorepo using the **Polylith** architecture pattern. The codebase is structured as follows:

- **Components** (`components/`): Reusable building blocks with interface/implementation separation
  - Each component has an `interface.clj` that defines the public API
  - Implementation is contained within the component's namespace
  - Key components include: system, log, env, http, sql-postgres, pubsub-pulsar, vault, encryption, iam
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

### Setup
```bash
# Install all dependencies and configure environment
just install
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
Applications use EDN configuration files with environment-specific profiles. The `env` component handles loading configuration from:
- `-c` flag for config file path
- `-p` flag for profile selection (dev, test, prod)

### Database Integration
- PostgreSQL integration via `sql-postgres` component
- Database migrations handled by `migrator` component using Liquibase
- Connection pooling and lifecycle managed by the system component

### Message Queue Integration
- Apache Pulsar integration via `pubsub-pulsar` component
- Supports encrypted messaging with RSA key pairs
- Topic and schema management built-in

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