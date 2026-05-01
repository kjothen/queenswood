# Bases

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
  src/com/repldriven/mono/<base-name>/
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

Pattern (adapted from `bases/bank-monolith/src/.../main.clj`):

```clojure
(ns com.repldriven.mono.<base>.main
  (:require
    [com.repldriven.mono.<some-component>.interface]
    [com.repldriven.mono.<another>.interface]
    ;; ... bare requires for every system-extending brick

    [com.repldriven.mono.<base>.api :as api]
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

Bases never depend on other bases. If two bases need to share
code, that code belongs in a component.

## Rules

**MUST:**

- Bases live in `bases/`.
- A base has a `-main` function in its entry namespace and uses
  `(:gen-class)`.
- Bases access components via `interface.clj`.
- Bases bare-require every brick whose system multimethods need
  to extend at runtime.

**MUST NOT:**

- Bases depend on other bases.
- Bases share code with each other except through components.

## Discussion

Bases are the runnable parts of the system. The split between
"base provides `-main` and bootstrap" and "project picks the
components" lets the same code run in different deployments —
the bank-api base for an HTTP-only project, the bank-monolith
base for an everything-in-one project.

The no-base-depends-on-base rule keeps the dep graph clean. If
two bases need shared logic, hoisting it into a component is the
right move; the alternative is a lattice of base-on-base deps
that loses the one-entry-point-per-artefact property.

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
- [projects.md](projects.md)
- [system-components.md](system-components.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
