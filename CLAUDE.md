# CLAUDE.md

Queenswood is a Clojure core-banking system, organised as a Polylith
workspace and built as a domain fork of
[`mono`](https://github.com/repldriven/mono). Bricks prefixed `bank-*`
are Queenswood-specific; everything else is shared infrastructure
inherited from upstream.

## Topic router

Before doing non-trivial work, open the doc that owns the topic and
work from it. CLAUDE.md is the routing layer; the rules and
rationale live in the docs.

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
- **Brick boundaries** — bricks react via changelog watchers
  rather than orchestrating across each other; `bank-api` stays
  ignorant of cross-brick effects.
  See [ADR-0008](docs/adr/0008-changelog-watchers.md) and
  [recipes/components.md](docs/recipes/components.md).
- **Processor bricks** — paired `bank-X-processor` base and
  `bank-X` component (commands / core / domain / store /
  watcher), the `txn-or-config` threading convention, FDB
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
- **Deployment** — Helm chart, Tilt + kind dev loop, per-service
  Docker images. See
  [recipes/deployment.md](docs/recipes/deployment.md).
- **Infrastructure** — GCP via Crossplane on a kind management
  plane; Argo CD wires the bootstrap chain; queenswood-platform
  Composites + Releases drive everything else. See
  [tdd/infrastructure.md](docs/tdd/infrastructure.md) and
  [ADR-0016](docs/adr/0016-crossplane-over-terraform.md).
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

## Critical guardrails

The rules most load-bearing across the codebase. Detail and
rationale live in the referenced docs.

- **No throwing from `interface.clj`.** Component interfaces
  return a value or an anomaly; they never raise. Use
  `error/try-nom` or `error/try-nom-ex` to convert exceptions
  at library boundaries.
  See [recipes/error-handling.md](docs/recipes/error-handling.md).
- **Use the `utility` brick.** `util/uuidv7` for IDs, `util/now`
  for timestamps; never `random-uuid`,
  `(System/currentTimeMillis)`, or `(Instant/now)` directly. For
  any helper not in `clojure.core`, check `utility` first.
  See [recipes/common-helpers.md](docs/recipes/common-helpers.md).
- **No `use-fixtures` in tests.** Manage system lifecycle with
  `with-test-system`; assert anomaly-freeness with `nom-test>`.
  See [recipes/testing.md](docs/recipes/testing.md).
- **Pull/merge from `main` before committing.** Renovate
  auto-merges dependency updates weekly.
  See [recipes/git-workflow.md](docs/recipes/git-workflow.md).
- **Cross brick boundaries only via `interface.clj`; wrap
  every library.** Reach other components through their
  `interface.clj`, never internal namespaces, and never list
  components in `deps.edn`. Every third-party library has
  exactly one wrapping brick; other bricks consume the wrapper,
  not the library directly.
  See [recipes/components.md](docs/recipes/components.md) and
  [ADR-0011](docs/adr/0011-one-component-per-third-party-library.md).
- **Minimal commentary on code.** Docstrings on `interface.clj`
  are the documentation surface; impl files stay bare. Inline
  `;;` comments are exceptional — only the load-bearing *why*
  (invariant, workaround, upstream constraint), never the *what*
  that the code already says.
  See [ADR-0015](docs/adr/0015-comments-and-docstrings.md).

## Common commands

```bash
# Run the full polylith test matrix (per service project)
clojure -M:poly test :all

# Run the development project (every brick — includes scenarios)
clojure -M:poly test project:dev :all

# Run tests for one or more bricks
clojure -M:poly test brick:<brick-name> project:dev
clojure -M:poly test brick:<brick1>:<brick2> project:dev

# Code generation prep
clj -X:deps prep :aliases '[:dev]'

# Bank-specific generation, forced after a schema change
clj -X:deps prep :aliases '[:+bank :dev]' :force true

# Pre-commit hook install (once per clone)
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```
