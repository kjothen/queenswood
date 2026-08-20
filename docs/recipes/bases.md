# Bases

<!-- tessl-plugin: framework -->

## Problem

You want to add or modify a Polylith base.

## Solution

We use bases in `bases/` as application entry points. A base owns
the `-main` function, parses CLI args, builds the system
definition, injects any required handlers, and starts the system.
Each runnable Queenswood application has exactly one base.

### File layout

```
bases/<base-name>/
  src/com/repldriven/queenswood/<base-name>/
    main.clj    ; -main entry point and bootstrap
    ...         ; (often) interceptors, handlers, route definitions
  test/...
  deps.edn
```

### main.clj

The base's `main.clj` does three things:

1. Bare-requires every brick whose system multimethods need to be
   extended at startup.
2. Defines `start` — builds the system definition from a YAML
   config, injects any `!system/required-component` slots, and
   calls `system/start`.
3. Defines `-main` to parse CLI args and call `start`.

Pattern (adapted from `bases/monolith/src/.../main.clj`):

```clojure
(ns com.repldriven.queenswood.<base>.main
  (:require
    [com.repldriven.queenswood.<some-component>.interface]
    [com.repldriven.queenswood.<another>.interface]
    ;; ... bare requires for every system-extending brick

    [com.repldriven.queenswood.<base>.api :as api]
    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn start
  [config-file profile]
  (nom-> (env/config config-file profile)
         system/defs
         (assoc-in [:system/defs :server :handler] api/app)
         system/start))

(defn stop [system] (system/stop system))

(defn -main
  [& args]
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "<base-name>" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false
                    (str "Failed to start [" (error/kind sys)
                         "]: " (or (:message sys) "Unknown error")))
          (do (log/info "System started successfully")
              @(promise)))))))
```

`(:gen-class)` exposes `-main` as a Java entry point. `@(promise)`
blocks the main thread so the JVM stays alive while the
background components run.

### Accessing components

Bases reach components through `interface.clj`, never internal
namespaces — the same rule as for components themselves:

```clojure
;; OK
[com.repldriven.mono.server.interface :as server]
```

Bases never depend on other bases, with one bounded exception: a
designated multi-base aggregator (see Discussion). If two bases need
to share code, that code belongs in a component.

A *composed* base — one bundled into an aggregator rather than
deployed on its own — carries an `interface.clj` like a component's,
so the aggregator reaches it by the same rule as everything else:

```clojure
(ns com.repldriven.queenswood.clearbank-simulator.interface
  "One-paragraph summary."
  (:require
    [com.repldriven.queenswood.clearbank-simulator.system]

    [com.repldriven.queenswood.clearbank-simulator.api :as api]))

(defn app
  "Ring handler for the ClearBank simulator's HTTP surface. ..."
  [ctx]
  (api/app ctx))
```

Requiring it registers the base's system component-kinds (via the
bare `.system` require) and exposes its handler, so an aggregator
needs one require per composed base instead of reaching into `.api`
and `.system` separately. `poly` does not recognise it as an
interface — the brick stays a base and gets no interface-mismatch
checking — so this is a convention the idioms hook enforces, not a
Polylith feature.

## Rules

**MUST:**

- Bases live in `bases/`.
- A base has a `-main` function in its entry namespace and uses
  `(:gen-class)` — unless it is a composed base, which has no project
  of its own and is entered only through an aggregator (see
  Discussion).
- A composed base has an `interface.clj` that bare-requires its own
  `system` namespace and exposes whatever the aggregator wires in
  (typically `app`).
- Bases access components via `interface.clj`.
- Bases bare-require every brick whose system multimethods need
  to extend at runtime.

**MUST NOT:**

- Bases depend on other bases — except a designated multi-base
  aggregator (see Discussion).
- An aggregator reach a composed base by anything but its
  `interface.clj`. `.api` is reserved for a base that has none.
- A base own a store. Persistence belongs in a component; a base may
  bare-require `fdb.interface` from its `system.clj` to register FDB
  component-kinds, and nothing more. Enforced by `store-in-a-base` in
  `scripts/hooks/enforce-idioms.sh`.
- Bases share code with each other except through components.

## Discussion

Bases are the runnable parts of the system. The split between
"base provides `-main` and bootstrap" and "project picks the
components" lets the same code run in different deployments
— a thin processor base per service group, the `api` base for the
HTTP service, and two aggregators composing other bases into one
in-process system: `monolith` (the whole bank, for local dev and
Testcontainers-backed end-to-end tests) and `external-adapters` (every
vendor adapter and its simulator). See [deployment.md](deployment.md)
for the per-service split that the production deployables
follow.

The no-base-depends-on-base rule keeps the dep graph clean. If
two bases need shared logic, hoisting it into a component is the
right move; the alternative is a lattice of base-on-base deps
that loses the one-entry-point-per-artefact property. An aggregator
base is the deliberate, bounded exception: it requires each composed
base's `interface.clj`, which extends that base's multimethods on load
and hands back the handler to wire into the aggregator's own system
definition. A base that only ever appears inside an aggregator is a
*composed* base: it has no project, so it has no `-main` of its own,
and the aggregator is its entry point. Giving it an interface is what
keeps the exception narrow — the dependency is still base-to-base,
but it crosses at a declared surface rather than reaching into
another base's internals.
The exception is scoped to the aggregators — it doesn't license
base-to-base dependency anywhere else. The composed bases today are
the ClearBank, Onfido and Companies House adapters and simulators.
They stay bases for the same reason `api` is one: each carries a
large surface — routes, handlers, examples, wire schemas — rather
than the require bundle a thin base amounts to. Moving that surface
into components is the standing alternative, not a pending step.

A base owning a store is a one-way door, which is why it is blocked
rather than discouraged. `component → base` is already disallowed, so
state parked behind an entry point is unreachable by any component —
the day one needs it, the store has to move first. It would also drop
out of the guarded set without saying so: `check-processors` iterates
`components/*`, and semgrep's `fdb-outside-store` is pathed to
`/components/*/src/**`, so neither would see a store that had moved
into `bases/`. A base still registers FDB component-kinds by
bare-requiring `fdb.interface` from its `system.clj`; that is
registration, not access.

The bare-require list in `main.clj` looks ugly but is
load-bearing: each entry extends the donut.system multimethods
the system definition needs at startup. Forgetting one means the
system fails to start with a "no method found" error. Tests, by
contrast, can consolidate these into a single
`test/.../system.clj` — see
[system-components.md](system-components.md).

## References

- [ADR-0001](../adr/0001-reuse-mono-as-upstream.md) — Reuse mono as upstream
- [ADR-0007 — System-as-data](../adr/0007-system-as-data.md)
- [components.md](components.md)
- [deployment.md](deployment.md)
- [projects.md](projects.md)
- [system-components.md](system-components.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
