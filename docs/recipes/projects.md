# Projects

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
projects/<project-name>/
  deps.edn
  resources/                  ; optional, deployment-scoped
    logback-test.xml          ; (currently) log filtering for tests
```

The system YAML config the base loads at startup does **not**
live here. It lives in the relevant component's or base's
`test-resources/` (or `resources/`) folder, loaded by classpath
URL — for example `classpath:bank-monolith/application-test.yml`.

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

Adapted from `projects/bank-monolith/deps.edn`:

```clojure
{:deps {component/bank-cash-account
        {:local/root "../../components/bank-cash-account"}
        component/bank-payment
        {:local/root "../../components/bank-payment"}
        ;; ... (every component the project ships)
        base/bank-monolith
        {:local/root "../../bases/bank-monolith"}
        ;; ... (every base the project ships)

        ;; Project-level pins
        org.clojure/clojure {:mvn/version "1.12.4"}
        com.google.protobuf/protobuf-java {:mvn/version "3.25.8"}}

 :aliases
 {:build {:deps {bases/build {:local/root "../../bases/build"}}
          :exec-args
          {:lib  'com.repldriven.mono/<project>
           :main com.repldriven.mono.<base>.main
           :major-minor-version "0.0"}
          :ns-default com.repldriven.mono.build.build}

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
than inside a component, typically for binary compatibility. The
`bank-monolith` project pins
`com.google.protobuf/protobuf-java` to 3.x because protojure
transitively brings 4.x, which breaks the FDB Record Layer at
runtime. This kind of pin lives in the project's `:deps`, not
in any single component.

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
  resources such as logback configuration. The system YAML
  config the base loads at startup does not belong here.

## Discussion

Projects exist so the same components can be assembled into
different deployables. The `bank-web` project pulls in the
bank-api base for an HTTP-only service; `bank-monolith` pulls
in several bases for an everything-in-one process. The
components are reused; only the project-level shape differs.

Keeping projects code-free has two benefits. First, a project
review reads like a deployment manifest, not a programming
exercise. Second, library version pins live in one obvious
place per deployable; a component never has to know which
project it's running in.

The project's optional `resources/` folder is on the classpath
at runtime and holds deployment-scoped resources — currently
just `logback-test.xml` for log filtering. The system YAML
configuration the base loads at startup is not here; it lives
in the relevant component's or base's `resources/` or
`test-resources/` folder and is loaded via a classpath URL
(for example `classpath:bank-monolith/application-test.yml`).
Profiles (`:dev`, `:test`, `:prod`) are encoded inside that
YAML via `aero` `!profile` tags, not through separate files.

## References

- [ADR-0001](../adr/0001-reuse-mono-as-upstream.md) — Reuse mono as upstream
- [ADR-0007 — System-as-data](../adr/0007-system-as-data.md)
- [bases.md](bases.md)
- [components.md](components.md)
- [system-components.md](system-components.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
