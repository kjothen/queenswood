# Projects
<!-- tessl-plugin: framework -->

## Problem

You want to assemble a Polylith project — pick the components
and bases that go into a deployable.

## Solution

We use projects in `projects/` as deployable assemblies. A
project is a `deps.edn` listing components and bases as
`:local/root` deps. It MAY also have a `resources/` folder for
deployment-scoped resources. No source code; no `-main` (bases
handle that).

### File layout

```
projects/<project-name>-service/
  deps.edn
  resources/                  ; deployment-scoped
    application.yml           ; the system definition the base loads
    logback.xml               ; production log config
    logback-test.xml          ; test log config
    bank/                     ; per-service domain config (optional)
      <domain>.yml            ; e.g. cash-account.yml on the
                              ;      cash-account processor service
```

For deployable services, the system YAML the base loads at
startup lives **here** — the runtime container picks it up
via `-c classpath:application.yml -p default`. The
`bank/<domain>.yml` files are domain-scoped includes
referenced from `application.yml` for processor services.

The test-only fall-through is different: the `monolith`
base (which bundles every component for in-process
end-to-end tests) loads its config from
`bases/monolith/test-resources/bank-monolith/application-test.yml`,
not from a project's `resources/`. See
[system-configurations.md](system-configurations.md).

### deps.edn pattern

A project's `deps.edn` typically has three sections:

- **`:deps`** — components and bases as `:local/root` paths,
  plus any project-level pins (Clojure version, library
  compatibility pins).
- **`:aliases :build`** — pulls in the `build` base and sets
  exec-args for `tools.build` to assemble the deployable.
- **`:aliases :test`** — extra components and bases that only
  tests need (`test-resources`, `test-system`, `testcontainers`),
  plus the test runner, which comes from `mono` via the
  `deps/mono-test-runner` shim rather than a base of our own.

Adapted from `projects/api-service/deps.edn`:

```clojure
{:deps {component/bank-cash-account
        {:local/root "../../components/cash-account"}
        component/bank-payment
        {:local/root "../../components/payment"}
        ;; ... (every component the project ships)
        base/bank-api
        {:local/root "../../bases/api"}
        ;; ... (every base the project ships)

        ;; Project-level pins
        org.clojure/clojure {:mvn/version "1.12.5"}
        pin/clojure-core-async {:local/root "../../deps/clojure-core-async"}}

 :aliases
 {:build {:deps {bases/build {:local/root "../../bases/build"}}
          :exec-args
          {:lib  'com.repldriven.queenswood/<project>
           :main com.repldriven.queenswood.<base>.main
           :major-minor-version "0.0"}
          :ns-default com.repldriven.queenswood.build.build}

  :test {:extra-deps
         {ext/mono-test-runner
          {:local/root "../../deps/mono-test-runner"}
          component/test-resources
          {:local/root "../../components/test-resources"}
          component/test-system
          {:local/root "../../components/test-system"}
          component/testcontainers
          {:local/root "../../components/testcontainers"}}}}

 :paths ["resources"]}
```

### Library pinning

Libraries that several bricks share, or that need holding at a
particular version for binary compatibility, are pinned once in a
shim under `deps/` and pulled in by `:local/root` — the same
pattern `deps/mono` uses for the upstream coordinate. Consumers
reference them under a `pin/` prefix:

- `pin/protojure` — protojure plus `protobuf-java` 3.x. protojure
  ships 4.x, which the FDB Record Layer cannot load, so the shim
  excludes protojure's copy outright.
- `pin/fdb` — `fdb-java` plus `fdb-record-layer-core`. The record
  layer ships an older `fdb-java`, which the shim excludes.
- `pin/clojure-core-async` — `core.async`.

A shim only controls a version if the coordinate reaches the
resolver at the depth it needs. Pinning *up* (holding a library
above what something else asks for) works unaided, because the
resolver's tie-break at equal depth takes the newer version.
Pinning *down* does not: the shim's copy sits one level below a
direct dependency and loses, so the competing copy has to be
excluded at the point it enters — which is what `pin/protojure`
and `pin/fdb` do.

`org.clojure/clojure` is the exception that cannot be shimmed at
all. The CLI merges its own root `deps.edn` into every project's
`:deps`, so Clojure is always a direct dependency and a coordinate
one level down never competes. A project that drops the pin does
not inherit the workspace version — it silently takes whatever
Clojure the caller's CLI ships. Every project therefore repeats
`org.clojure/clojure`, and `just check-versions` asserts the
copies against the root `deps.edn`.

## Rules

**MUST:**

- Projects live in `projects/`.
- A project contains a `deps.edn`.
- Projects use `:local/root` paths for components and bases.
- A project that produces a deployable artefact has a `:build`
  alias pointing at `bases/build`.

**MUST NOT:**

- Projects contain Clojure source code.
- Projects define a `-main` (bases do).
- Projects depend on other projects.

**MAY:**

- Projects have a `resources/` folder for deployment-scoped
  resources: `application.yml` (the system definition),
  `logback.xml` and `logback-test.xml`, and an optional
  `bank/` subfolder of domain-scoped includes referenced
  from `application.yml`.

## Discussion

Projects exist so the same components can be assembled into
different deployables. Today's deployables come in three
shapes:

- **HTTP services** — `api-service`, the
  `clearbank-{adapter,simulator}-service` pair, the
  `onfido-{adapter,simulator}-service` pair, each
  pulling in its corresponding base.
- **Message-bus processor services** — one per command
  processor: cash-account, party, payment, interest,
  transaction, idv.
- **One-shots** — `migrator-service` and
  `bootstrap-service` for the cold-start chain.

Plus a test-only outlier: the `monolith` base bundles
every component into one in-process system for end-to-end
tests. It has no corresponding deployable project; it's
booted under Testcontainers from a test harness.

Keeping projects code-free has two benefits. First, a
project review reads like a deployment manifest, not a
programming exercise. Second, library version pins live in
one obvious place per deployable — a component never has to
know which project it's running in.

The project's `resources/` folder is on the classpath at
runtime. For deployable services it holds `application.yml`
(the system definition the base loads at startup, picked up
via `-c classpath:application.yml -p default`),
`logback.xml` for production log config, `logback-test.xml`
for tests, and an optional `bank/` subfolder of
domain-scoped includes that `application.yml` references.
Profiles (`:dev`, `:test`, `:prod`) are encoded inside the
YAML via `aero` `!profile` tags, not through separate
files.

The single shared Dockerfile parameterised by
`PROJECT_NAME` makes the per-service split tractable —
adding a service is a project, a base, and a chart entry,
not a fresh image-build pipeline. See
[deployment.md](deployment.md) for the full story.

## References

- [ADR-0001](../adr/0001-reuse-mono-as-upstream.md) — Reuse mono as upstream
- [ADR-0007 — System-as-data](../adr/0007-system-as-data.md)
- [bases.md](bases.md)
- [components.md](components.md)
- [deployment.md](deployment.md)
- [system-components.md](system-components.md)
- [system-configurations.md](system-configurations.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
