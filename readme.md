# mono

A Clojure monorepo for building production-ready distributed systems, organised
as a [Polylith](https://polylith.gitbook.io/polylith) workspace.

## What It Is

`mono` is a component library and reference implementation for composing
production systems from well-defined, independently testable building blocks.
Systems are described as data — YAML/EDN configuration files drive lifecycle,
dependency injection, and environment management, with no global state and no
framework magic.

## Architecture

### Polylith

Three artifact types live in this repo:

| Type | Location | Role |
|---|---|---|
| **Components** | `components/` | Reusable building blocks with a stable public interface |
| **Bases** | `bases/` | Application entry points (`-main`, HTTP handlers, processors) |
| **Projects** | `projects/` | Deployable applications — just `deps.edn`, no code |

Components expose a single `interface.clj`. Nothing in this repo reaches
into another component's internals.

### System-as-Data

A running application is the product of a configuration file:

```
config (YAML/EDN) → system definitions → started system
```

Components register themselves via `system/defcomponents`. Projects wire
components together by listing them in `deps.edn`. Infrastructure — databases,
message queues, Vault — is just another system component.

### Error Handling

No exceptions cross component boundaries. All failure paths return anomalies:

```clojure
;; Short-circuits on first failure
(error/let-nom> [conn   (db/connect datasource)
                 result (sql/execute conn query)]
  result)
```

Macros — `try-nom`, `let-nom>`, `nom->`, `nom-do>` — compose anomaly-aware
pipelines without defensive `try/catch` noise.

## Components

### Foundation

| Component | Purpose |
|---|---|
| `system` | Lifecycle management wrapping `donut.system` |
| `error` | Anomaly-based error handling (`nom` library) |
| `env` | Configuration loading with `:dev`/`:test`/`:prod` profiles |
| `log` | Structured logging |
| `utility` | Deep merge, UUID v7, YAML conversion, collection helpers |
| `spec` | Malli-based validation with human-readable errors |

### Persistence

| Component | Purpose |
|---|---|
| `db` | PostgreSQL with connection pooling |
| `sql` | HoneySQL query formatting |
| `migrator` | Liquibase schema migrations |
| `fdb` | FoundationDB — KV layer, record layer, changelog processing |

### Messaging

| Component | Purpose |
|---|---|
| `pulsar` | Apache Pulsar producer/consumer/reader with Avro |
| `mqtt` | MQTT publish/subscribe |
| `message-bus` | Protocol abstraction over messaging backends |
| `command` | Request-reply and async command dispatch over message-bus |

### Web & HTTP

| Component | Purpose |
|---|---|
| `server` | Jetty with interceptor-based dependency injection and OpenAPI |
| `http-client` | HTTP client with anomaly-based error handling |

### Security & Cryptography

| Component | Purpose |
|---|---|
| `vault` | HashiCorp Vault for secrets and key management |
| `encryption` | AES-256, RSA, base64 |
| `pulsar-vault-crypto` | Tenant-scoped Pulsar message encryption via Vault |

### Serialisation

| Component | Purpose |
|---|---|
| `avro` | Apache Avro schema-based serialisation |
| `schema` | Protobuf definitions (Person, AddressBook, Account) |
| `json` | JSON read/write with anomaly errors |

### Observability

| Component | Purpose |
|---|---|
| `telemetry` | OpenTelemetry tracing with W3C traceparent propagation |

### Testing

| Component | Purpose |
|---|---|
| `test-system` | `with-test-system` lifecycle macro, `nom-test>` assertions |
| `testcontainers` | Declarative container infrastructure for integration tests |
| `test-resources` | Shared test configuration |

## Deployed Applications

| Project | Base | Description |
|---|---|---|
| `accounts-web` | `accounts-api` | Account lifecycle API (open, close, suspend, …) |
| `iam-web` | `iam-api` | Service account management |
| `accounts-processor` | `command-processor` | Async command handler for account operations |
| `keyring-web` | `symmetric-key-api` | Symmetric key management API |

## Getting Started

### Prerequisites

- Clojure CLI
- Docker (for integration tests via Testcontainers)

### Run all tests

```bash
clojure -M:poly test project:dev
```

### Test a specific component

```bash
clojure -M:poly test brick:command project:dev
```

### REPL

```bash
clojure -M:dev:nrepl
# or with Rebel Readline
clojure -M:dev:rebel
```

### Code formatting

```bash
clojure -M:format/zprint --formatted path/to/file.clj
```

### Linting

```bash
clojure -M:lint/clj-kondo --lint bases components
```

## Key Patterns

**Referential transparency** — functions return the same value for the same
inputs. Side effects are pushed to the edges.

**Keyword keys throughout** — all data, including from Pulsar, MQTT, and HTTP
request bodies, uses kebab-case keyword keys.

**No global state** — systems are values; started systems are maps.

**Interceptor injection** — HTTP handlers receive datasources, message clients,
and other dependencies through request context, not through dynamic vars or
atoms.

**Testcontainers as system components** — PostgreSQL, Pulsar, Vault, and other
infrastructure are declared in test YAML configs and managed by the same
lifecycle machinery used in production.

## Tooling

- **[Polylith](https://polylith.gitbook.io/)** — workspace management and
  incremental testing
- **[zprint](https://github.com/kkinnear/zprint)** — code formatting (80-char
  width, enforced by pre-commit hook)
- **[clj-kondo](https://github.com/clj-kondo/clj-kondo)** — linting (enforced
  by pre-commit hook)
- **[Renovate](https://docs.renovatebot.com/)** — automated dependency updates
