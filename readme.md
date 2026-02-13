# mono

[![Tests](https://github.com/kjothen/mono/actions/workflows/test.yml/badge.svg)](https://github.com/kjothen/mono/actions/workflows/test.yml)

A Clojure monorepo providing reusable components for building end-to-end systems. This project uses the Polylith framework and includes several working examples demonstrating real-world patterns. With comprehensive testcontainers support, it enables complex integration test environments that replicate production deployments end-to-end.

## Architecture

This is a Clojure monorepo using the **Polylith** architecture pattern:

- **Components** - Reusable building blocks with interface/implementation separation
- **Bases** - Application entry points and web APIs
- **Projects** - Deployable applications combining components and bases

## Key Features

### Data-Driven System Configuration
Systems are defined as data in YAML (preferred) or EDN with environment-specific profiles:
- Substantial **testcontainers** support for spinning up test infrastructure
- **Interceptor-based dependency injection** - handlers access system components (datasources, message queues, etc.) via request context
- All data-driven - no global state, no need to wire working systems together with code

### Key Integrations
- **Jetty** - Embedded web server with interceptor support
- **Apache Pulsar** - Message queue with encryption support and channel-based async patterns
- **PostgreSQL** - Database integration
- **Liquibase** - Database schema migrations and version control
- **Vault** - Secrets management and cryptographic key storage
- **Avro** - Schema-based serialization
- **MQTT** - IoT messaging protocol support

### Anomaly-Based Error Handling
Most interface functions return values or anomalies (not exceptions) using the `error` component:
- `error/let-nom` - monadic let that short-circuits on errors
- `error/anomaly?`, `error/kind`, `error/fail` - for working with anomalies

## Getting started

- See [Justfile](./Justfile) for a list of useful commands

---

<img src="logo.png" width="30%" alt="Polylith" id="logo">

The Polylith documentation can be found here:

- The [high-level documentation](https://polylith.gitbook.io/polylith)
- The [Polylith Tool documentation](https://github.com/polyfy/polylith)
- The [RealWorld example app documentation](https://github.com/furkan3ayraktar/clojure-polylith-realworld-example-app)

You can also get in touch with the Polylith Team via our [forum](https://polylith.freeflarum.com) or on [Slack](https://clojurians.slack.com/archives/C013B7MQHJQ).
