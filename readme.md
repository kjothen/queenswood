<!-- markdownlint-configure-file { "MD033": false, "MD041": false } -->

<p align="center">
  <img src="docs/assets/logo.svg" alt="Queenswood" width="200" />
</p>

# Queenswood

**Core banking, boxed.** You want a modern banking platform — without
building it all yourself, or renting one you can't see inside.
Queenswood is the operational core — accounts, payments, a
double-entry ledger, interest, onboarding, and policies — the
machinery of a bank. You use your banking licence. You
contract with identity and payment-rails providers, plug them in
where supported, or extend the platform where not.

The world runs on banking it never sees. Queenswood makes the core a
commodity — out in the open, yours to read and run.

## Demos

<table>
  <tr>
    <td align="center"><strong>Spin up a Bank</strong></td>
    <td align="center"><strong>Use the Bank</strong></td>
  </tr>
  <tr>
    <td><video src="https://github.com/user-attachments/assets/1dcf2a74-b198-4757-b271-84896f0daec8" controls></video></td>
    <td><video src="https://github.com/user-attachments/assets/5f5403c2-3ada-4985-826b-209e1826f550" controls></video></td>
  </tr>
</table>

## What it does

Everything a bank needs, from the same API:

- **Accounts** — open, close, suspend/resume with sort code addresses and balances
- **Account products** — current and savings account product versioning
- **Interest** — accrual, capitalisation and fractional carry
- **Ledger** — double-entry postings on every money movement
- **Onboarding & identity** — know-your-customer checks and onboarding
- **Parties** — customer records, with retrieval and merge
- **Payee checks** — verify a payee before an outbound payment
- **Payments** — internal transfers, inbound and outbound payments
- **Policies** — restrictions and limits as configurable policy
- **Scheduling** — recurring jobs on an operator cadence
- **Unlimited banks** — spin-up multiple banks, in test or live

API reference:
[repldriven.github.io/queenswood](https://repldriven.github.io/queenswood/),
or live OpenAPI at [localhost:8080](http://localhost:8080) when
running.

## Architecture

The Message Bus (Kafka or Pulsar) carries commands and events
between Queenswood's processors and external providers; a distributed
database (FoundationDB) manages the data.

<picture>
  <source media="(prefers-color-scheme: dark)"  srcset="docs/diagrams/system-diagram-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/diagrams/system-diagram-light.svg">
  <img alt="Queenswood system diagram" src="docs/diagrams/system-diagram-light.svg">
</picture>

**Writes are idempotent commands.** The API write-side turns a request into a
command on the bus; a processor consumes it, and does the **entire**
operation in one database transaction. Processors can be deployed
individually or, for maximum efficiency, bundled according to lines
of responsibility, such as financial and operational processor bundles.

**Reads are queries.** The API read-side loads records directly using a
separate query surface — no command round-trip and no overlap with
the write-path, top-to-bottom.

**Choreography through a changelog.** Every committed write lands on
FoundationDB's changelog in order, atomically with the write — the
system's transactional outbox. The rule is commit first, relay
after: nothing is sent from inside a transaction. A processor
writes what to emit alongside its state change, and a relay drains
the changelog to publish it to the bus, so processors react to one
another, event-sourced.

**Egress through intent.** An external call is the same
commit-then-relay pattern, one boundary further out. An HTTP
round-trip can't run inside a database transaction, so the write records an
intent; a relay makes the call afterwards and folds the provider's
webhook back in as an event.

## What's interesting

The decisions that make this codebase worth reading — each with a
doc that goes deep:

- **One unified API for the whole bank, with full OpenAPI 3.x
  compliance.** See
  [ADR-0013](docs/adr/0013-single-unified-api.md) and
  [ADR-0014](docs/adr/0014-openapi-3x-compliance.md).
- **Policies and bindings as first-class data, not hardcoded
  rules.** Combining allow/deny capabilities with
  sophisticated time and volume-based limits, affords
  fine-grained policies where you need it most.
  See [policy-evaluation](docs/tdd/policy-evaluation.md).
- **Daily interest accrual, capitalisation and carry.** Integer
  micro-unit arithmetic with sub-minor-unit carry; six-leg
  postings at capitalisation; with cadence (daily, monthly, anything)
  being your choice. See [interest](docs/tdd/interest.md).
- **System-level and model-equality property testing**.
  Two parallel state machines, the real system and an independent model,
  fed the same commands, with end states compared.
  See [scenario-testing](docs/tdd/scenario-testing.md).
- **Anomalies, not exceptions, at every component interface.**
  Failure is a first-class return value. Every caller engages with
  it; nothing slips by silently.
  See [ADR-0005](docs/adr/0005-error-handling-with-anomalies.md).
- **System-as-data** Test and production share one bootstrap path.
  See [ADR-0007](docs/adr/0007-system-as-data.md) and the
  [slides](docs/slides/systems-as-data/slides.md).
- **FoundationDB Record Layer.** Multi-record ACID by default; the
  transactional outbox pattern falls out of the storage engine.
  See [ADR-0002](docs/adr/0002-foundationdb-record-layer.md).
- **Consumes `mono`** — shared infrastructure pulled in as a
  pinned git-dependency, not forked into the workspace.
  See [ADR-0001](docs/adr/0001-reuse-mono-as-upstream.md).

## Documentation

The bank is documented end to end — the why, the how, and the
decisions in between:

- **[docs/prd/](docs/prd/)** — product requirements documents:
  a platform-wide umbrella plus one per capability (onboarding,
  parties, cash-account-products, cash-accounts, payments,
  interest, policies). The _what and why_ — intended scope,
  users, and domain rules — companion to the TDDs' _how_.
- **[docs/tdd/](docs/tdd/)** — technical design documents
  covering the substrate (transaction processing, transactions
  and balances, traceability, scenario testing, idempotency
  proposal), the API surface and auth (service-apis,
  authentication), the policy engine, and every domain (banks,
  parties, products, accounts, payments, interest).
- **[docs/adr/](docs/adr/)** — architecture decision records
  (mono fork, FoundationDB, message-bus abstraction, Avro,
  anomalies, kebab-case keys, system-as-data, changelog relay,
  model-equality testing, code generation via prep-lib,
  one-component-per-library, pre-commit hooks, single unified
  API, OpenAPI 3.x compliance, comments and docstrings).
- **[docs/slides/](docs/slides/)** — a slidev walk-through of how
  systems-as-data assembles a running system.
- **[docs/recipes/](docs/recipes/)** — task-oriented recipes
  (Problem / Solution / Rules / Discussion / References) for
  components, bases, projects, system-components,
  system-configurations, testcontainers, error-handling, testing,
  code-style, code-generation, common-helpers, deployment,
  git-workflow, writing-docs.

## Running

Two options: a REPL-driven dev loop with everything inside
Testcontainers, or a one-liner Helm install onto a local
Kubernetes cluster.

The dev environment comes from the nix devshell. Check that the
native toolchain on `PATH` is the one this workspace pins:

```bash
just doctor
```

Those versions live in `versions.json`, the single source that
`flake.nix` and CI both build from. Two of them are exact rather
than minimums: the FoundationDB client must share a protocol
version with the server the Testcontainers image builds, and
`protoc` must stay on the line that emits code for the pinned
`protobuf-java`. Both fail at runtime rather than at build time,
which is what `just doctor` exists to catch.

The pins that cannot read `versions.json` — the Dockerfile
`ARG` defaults, the Helm values, and the `fdb-java` coordinate in
`deps/fdb` — are asserted against it by `just check-versions`,
which also gates the test workflow. The same command checks the
per-project `org.clojure/clojure` pins against the root
`deps.edn`; Clojure is the one library that cannot be pinned from
a single place, because the CLI merges its own version into every
project as a direct dependency.

JVM library versions otherwise live in shims under `deps/`, pulled
in by `:local/root` under a `pin/` prefix, so a coordinate shared
across bricks is written once. See
[recipes/projects.md](docs/recipes/projects.md#library-pinning).

### REPL (Testcontainers)

Start a REPL with `just repl` and connect your editor. The
development entry point follows the standard Polylith pattern —
a namespace under `development/src/dev/` that requires the base
and Testcontainers:

```clojure
;; development/src/dev/bank_monolith.clj — evaluate the comment block
(def sys
  (main/start "classpath:monolith/application-test.yml" :dev))
(main/stop sys)
```

This boots the full system — FoundationDB, Message Bus (Kafka or Pulsar),
HTTP server — inside Testcontainers. Then start the Svelte front-end:

```bash
just console-start
```

### Kubernetes

The released Helm chart deploys the entire platform (API,
processors, adapters/simulators, the Svelte front-end,
Kafka or Pulsar, FoundationDB) onto any Kubernetes cluster.

**Get a local Kubernetes runtime on macOS**, pick any:

- **OrbStack** — single-app, fastest setup:

  ```bash
  brew install orbstack
  # Open OrbStack, enable Kubernetes in Settings → Kubernetes
  ```

  Enabling Kubernetes here _is_ the cluster — there's no separate
  `kind create cluster` step. Skip straight to **Install** below.

- **kind on Colima** — closer to upstream, more configurable:

  ```bash
  brew install colima kind kubectl helm
  colima start --vm-type vz --vz-rosetta --cpu 6 --memory 24
  kind create cluster --name queenswood \
    --config <(curl -fsSL https://raw.githubusercontent.com/repldriven/queenswood/main/infra/kind/queenswood-config.yaml)
  ```

**Install** — both paths now have a running cluster, so from here the
steps are identical:

```bash
helm install queenswood \
  oci://ghcr.io/repldriven/queenswood \
  -n queenswood --create-namespace \
  --wait --timeout 10m
```

**Reach the API and console**:

```bash
kubectl -n queenswood port-forward svc/queenswood-api-service 8080:8080
kubectl -n queenswood port-forward svc/queenswood-console     8081:8080
```

The full quickstart — including tear-down — ships with
each
[release](https://github.com/repldriven/queenswood/releases/latest).

## Built on mono

Queenswood **consumes**
[mono](https://github.com/repldriven/mono) — a Clojure component
library for production-ready distributed systems built on
[Polylith](https://polylith.gitbook.io/polylith) — as a pinned
git-dependency. The workspace holds only Queenswood's own domain
bricks (`com.repldriven.queenswood.*`); the shared infrastructure
comes from the dependency (`com.repldriven.mono.*`), pinned to a
tag/sha via the `ext/mono` shims under `deps/` and upgraded with a
one-line bump.
See [ADR-0001](docs/adr/0001-reuse-mono-as-upstream.md) for the
reasoning. The shared component library (lifecycle,
persistence, messaging, security, etc.) is documented in the
[mono README](https://github.com/repldriven/mono#mono-components).

For the workspace layout, see `components/`, `bases/`, and
`projects/`. Brick conventions are documented in
[recipes/components](docs/recipes/components.md).
