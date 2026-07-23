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
  tests need (`test-resources`, `test-system`, `testcontainers`,
  `external-test-runner`).

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
        org.clojure/clojure {:mvn/version "1.12.4"}
        com.google.protobuf/protobuf-java {:mvn/version "3.25.8"}}

 :aliases
 {:build {:deps {bases/build {:local/root "../../bases/build"}}
          :exec-args
          {:lib  'com.repldriven.queenswood/<project>
           :main com.repldriven.queenswood.<base>.main
           :major-minor-version "0.0"}
          :ns-default com.repldriven.queenswood.build.build}

  :test {:extra-deps
         {bases/external-test-runner
          {:local/root "../../bases/external-test-runner"}
          component/test-resources
          {:local/root "../../components/test-resources"}
          component/test-system
          {:local/root "../../components/test-system"}
          component/testcontainers
          {:local/root "../../components/testcontainers"}}}}

 :paths ["resources"]}
```

### Project-level library pinning

Some libraries need to be pinned at the project level rather
than inside a component, typically for binary compatibility.
Service projects pin `com.google.protobuf/protobuf-java` to 3.x
because protojure transitively brings 4.x, which breaks the FDB
Record Layer at runtime. This kind of pin lives in the project's
`:deps`, not in any single component.

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
- **Pulsar processor services** — one per command
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
