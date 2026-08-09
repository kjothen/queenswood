# CLAUDE.md

Queenswood is a Clojure core-banking system, organised as a Polylith
workspace that consumes shared infrastructure from
[`mono`](https://github.com/repldriven/mono) as a pinned git-dependency
(the `ext/mono` shims under `deps/`). The workspace holds only
Queenswood's own domain bricks — `com.repldriven.queenswood.*`; the
shared infra lives in the dependency as `com.repldriven.mono.*`. See
[ADR-0001](docs/adr/0001-reuse-mono-as-upstream.md).

## Topic router

CLAUDE.md is the routing layer. Every `docs/recipes/*.md` and
`docs/adr/*.md` file below is labeled `<!-- tessl-plugin: <name> -->`,
and that plugin's rule (always loaded via `AGENTS.md`) already
distills its `## Rules` / `## Decision` — you don't need to open the
doc to rediscover that. Open it for the *why* behind the rule instead:
Context, Consequences, Discussion. `docs/tdd/`, `docs/prd/`, and
`docs/plan/` docs are the exception — nothing distills them, so open
those in full before non-trivial work on their topic.

### Code

- **Clojure code style** — naming, requires, destructuring, anon
  fns, `cond->`, `let`-binding format, ID generation (`util/uuidv7`),
  timestamps (`util/now`), interceptor short-circuit
  (`sieppari.context/terminate`).
  See [recipes/code-style.md](docs/recipes/code-style.md).
- **Common helpers** — when to add a helper to `utility`, when to
  re-export from a library (medley etc.), the convergence rule.
  See [recipes/common-helpers.md](docs/recipes/common-helpers.md).
- **Component interfaces and docstrings** — `interface.clj` is the
  documentation surface; impl files stay bare.
  See [ADR-0015](docs/adr/0015-comments-and-docstrings.md) and
  [recipes/components.md](docs/recipes/components.md).
- **Error handling** — anomalies at component boundaries; never
  throw from `interface.clj`; `error/try-nom` and `error/nom->` at
  library edges. See
  [ADR-0005](docs/adr/0005-error-handling-with-anomalies.md) and
  [recipes/error-handling.md](docs/recipes/error-handling.md).
- **Data shapes** — kebab-case keyword keys throughout, with
  string-typed currency (ISO 4217) as a deliberate exception.
  See [ADR-0006](docs/adr/0006-kebab-case-keyword-keys.md).

### Architecture

- **System wiring** — `system/defcomponents`, `system.clj` vs
  `system/` folder, the test-bundle pattern, naming shared
  resource components without baking environment names in.
  See [recipes/system-components.md](docs/recipes/system-components.md)
  and [ADR-0007](docs/adr/0007-system-as-data.md).
- **Brick boundaries** — bricks react to events relayed off
  each other's changelogs rather than orchestrating across each
  other; `bank-api` stays ignorant of cross-brick effects.
  See [ADR-0021](docs/adr/0021-changelog-relay.md) and
  [recipes/components.md](docs/recipes/components.md).
- **Transactional guarantees** — the line between work inside FDB
  (one transaction, commit-then-ack) and work that crosses a
  boundary (ingress idempotent consume-then-ack; egress via an
  outbox for events and an intent for external calls, drained by
  a relay). The external adapters (ClearBank, Onfido) are the
  worked example.
  See [tdd/transaction-processing.md](docs/tdd/transaction-processing.md),
  [tdd/payments.md](docs/tdd/payments.md), and
  [ADR-0021](docs/adr/0021-changelog-relay.md).
- **Processor bricks** — paired `bank-X-processor` base and
  `bank-X` component (commands / core / domain / store /
  events), the `txn-or-config` threading convention, FDB
  confined to `store.clj`, rejections originating in
  `domain.clj`.
  See [tdd/processor-bricks.md](docs/tdd/processor-bricks.md).
- **Bases and projects** — entry points, per-service projects,
  the development project that includes everything.
  See [recipes/bases.md](docs/recipes/bases.md) and
  [recipes/projects.md](docs/recipes/projects.md).
- **System configurations** — YAML system definitions, profiles,
  `!system/component` / `!system/ref` / `!env`.
  See [recipes/system-configurations.md](docs/recipes/system-configurations.md).
- **Service APIs** — Reitit + Sieppari + Muuntaja, RFC 9457
  problem details, two-tier auth, OpenAPI assembly. The API style
  is resource-based, not CRUD-shaped.
  See [tdd/service-apis.md](docs/tdd/service-apis.md),
  [ADR-0013](docs/adr/0013-single-unified-api.md),
  [ADR-0014](docs/adr/0014-openapi-3x-compliance.md).
- **Lifecycle transitions** — the ten-point definition-of-done for
  a new entity state or transition, source-state guards in
  `domain.clj` (`:<entity>/invalid-status`, HTTP 409), and event-
  handler guards as an idempotency gate rather than a rejection.
  See [recipes/lifecycle-transitions.md](docs/recipes/lifecycle-transitions.md)
  and [ADR-0021](docs/adr/0021-changelog-relay.md).

### Tests

- **General testing** — `with-test-system`, `nom-test>`, no
  `use-fixtures`, brick-level vs project-level test runs.
  See [recipes/testing.md](docs/recipes/testing.md).
- **Testcontainers** — FDB and Pulsar containers, reuse, image
  selection. See
  [recipes/testcontainers.md](docs/recipes/testcontainers.md).
- **Scenario testing** — two sibling scenario bricks, both
  data-driven EDN + fugato-style runner. `bank-test-scenarios`
  drives the domain layer for model-equality property tests;
  `bank-test-api-scenarios` drives the HTTP surface (`bank-api`)
  via real requests and is the home for what used to be
  per-base / per-component `*_test.clj` API tests.
  See [tdd/scenario-testing.md](docs/tdd/scenario-testing.md).

### Writing docs

- **Markdown formatting, mermaid, tone, PRD register** — wrap at
  80, link hygiene, no semicolons in mermaid labels, no maturity
  overclaim, no competitor names, PRDs use product language and
  describe what users do via "the banking API" rather than
  naming operations. See
  [recipes/writing-docs.md](docs/recipes/writing-docs.md).

### Operations

- **Git workflow** — merge `main` before committing (Renovate
  auto-merges deps weekly), stage user-initiated deletions and
  moves with `git add` not `git rm`, include the user's
  untracked drafts in workspace-wide ops.
  See [recipes/git-workflow.md](docs/recipes/git-workflow.md).
- **Code generation** — protoc + protojure for protobuf, Lancaster
  for Avro, prep alias for the bank profile.
  See [recipes/code-generation.md](docs/recipes/code-generation.md)
  and [ADR-0010](docs/adr/0010-code-generation-via-prep-lib.md).
- **Deployment** — Helm chart, kind dev loop, per-service
  Docker images. See
  [recipes/deployment.md](docs/recipes/deployment.md).
- **Cloud deployment** — running on GCP from GHCR-published
  charts and images, which tier stays up between sessions and
  which cycles, and the up/down runbook. See
  [recipes/cloud-deployment.md](docs/recipes/cloud-deployment.md).
- **Recovery** — restoring FoundationDB from backup: the routine
  teardown/rebuild cycle, choosing an earlier restore point, and why
  a stuck restore blocks the whole deployment. See
  [recipes/recovery-procedures.md](docs/recipes/recovery-procedures.md).
- **Infrastructure** — GCP via Crossplane on a kind management
  plane; Argo CD wires the bootstrap chain; queenswood-platform
  Composites + Releases drive everything else. See
  [tdd/infrastructure.md](docs/tdd/infrastructure.md) and
  [ADR-0016](docs/adr/0016-crossplane-over-terraform.md).
- **Cloud foundation** — the folder/seed/hub-and-spoke project
  layout, one management plane on GKE, foundations liened rather
  than deleted, and why "down" is a declared state. See
  [ADR-0022](docs/adr/0022-cloud-foundation-and-environment-lifecycle.md).
- **Cloud naming** — the installation code, the prefix/code/env/label
  rule and its exceptions, the inventory of every kind and a worked
  example of one installation. See
  [recipes/cloud-naming.md](docs/recipes/cloud-naming.md) and
  [ADR-0023](docs/adr/0023-installation-naming-and-access.md).
- **Pre-commit hooks** — zprint, clj-kondo, before-commit
  formatting. See
  [ADR-0012](docs/adr/0012-pre-commit-hooks.md).

### Domain reference

- **Per-capability designs** — `docs/tdd/` has one TDD per
  capability or subsystem (authentication, banks,
  cash-account-products, cash-accounts, idempotency,
  infrastructure, interest, onboarding, parties, payments,
  policy-evaluation, scenario-testing, service-apis,
  traceability, transaction-processing,
  transactions-and-balances).
- **Per-capability requirements** — `docs/prd/` has the
  product-shaped requirements (cash-account-products,
  cash-accounts, interest, memberships, onboarding, parties,
  payments, platform, policies, users).
- **In-flight implementation plans** — `docs/plan/`.

## Guardrails

The rules most load-bearing across the codebase — no throwing from
`interface.clj`, ID/timestamp generation via `utility`, no
`use-fixtures` in tests, pulling `main` before committing, crossing
brick boundaries only via `interface.clj`, minimal inline commentary —
live in the Tessl rule plugins (`idioms`, `framework`, `workflow`),
always loaded via `AGENTS.md`. See
[plugins/README.md](plugins/README.md) for the full plugin map. They
don't need restating here, and they're already in context on every
task. Don't hand-add a guardrail bullet to this file again — edit the
rule (and its source recipe/ADR, kept in sync by the
`sync-rules-from-docs` skill) instead.

## Common commands

```bash
# Run the full polylith test matrix (per service project)
clojure -M:poly test :all

# Run the development project (every brick — includes scenarios)
clojure -M:poly test project:dev :all

# Run tests for one or more bricks
clojure -M:poly test brick:<brick-name> project:dev
clojure -M:poly test brick:<brick1>:<brick2> project:dev

# Code generation prep (add :force true after a schema change)
clj -X:deps prep :aliases '[:dev]'

# Install the pre-commit hook (once per clone, and again whenever
# scripts/hooks/pre-commit changes — the hook is a copy, not a symlink)
just install-hooks
```

@AGENTS.md
