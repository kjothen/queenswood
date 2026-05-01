# Testing

## Problem

You want to write a test for some part of Queenswood.

## Solution

We use two test forms, chosen by what's being tested:

- **`clojure.test/deftest`** for pure functions in isolation —
  things that can be exercised by passing values and asserting
  on the return.
- **The scenario runner** for system-level behaviour — anything
  that touches the command pipeline, persisted state, or
  multi-component interaction. Fugato property tests and
  hand-authored EDN scenarios share the same runner.

System tests manage lifecycle explicitly with `with-test-system`
— not `use-fixtures` — and assert anomaly-freeness with
`nom-test>`.

### Running tests

The default project is `project:dev`. All tests:

```bash
clojure -M:poly test project:dev
```

Specific bricks (one or more, colon-separated):

```bash
clojure -M:poly test brick:<brick-name> project:dev
clojure -M:poly test brick:bank-balance:bank-cash-account project:dev
```

### Choosing a test form

- **Pure function, deterministic** → `deftest`.
- **Touches FDB, the message bus, or any real infrastructure**
  → scenario runner.
- **Cross-component behaviour** → scenario runner.
- **Specific case to lock down explicitly** → EDN scenario.
- **Exploring command sequences for bugs** → fugato property
  test.

Don't write `deftest`-style integration tests against the
command pipeline. The scenario runner is the only sanctioned
path for system-level tests. See
[ADR-0009](../adr/0009-model-equality-property-testing.md) and
[docs/design/scenario-testing.md](../design/scenario-testing.md)
for the architecture.

### with-test-system

`with-test-system` starts a test system from a YAML config,
asserts it started cleanly, and stops it after the body — no
`use-fixtures` ceremony, no `try`/`finally`, no global state.

```clojure
;; Simple form
(with-test-system
  [sys "classpath:my-component/application-test.yml"]
  (let [component (system/instance sys [:path :to :component])]
    ;; test body
    ))

;; With patch-fn — inject a handler or override a component
;; before the system starts
(with-test-system
  [sys ["classpath:server/application-test.yml"
        (fn [defs] (assoc-in defs [:system/defs :server :handler] app))]]
  ;; test body
  )
```

The optional second element of the binding vector is a patch-fn
applied to the system defs before start. Use it to inject HTTP
handlers — analogous to base-level required-component injection;
see [system-configurations.md](system-configurations.md) — or to
swap a component for a test double.

### nom-test>

`nom-test>` chains operations as let-style bindings, failing
fast on any anomaly and asserting no anomaly occurred. Use `_`
for bindings whose values are only needed for `is` assertions:

```clojure
(nom-test> [result1 (operation1)
            _       (is (= expected result1))
            result2 (operation2 result1)
            _       (is (some? result2))])
```

For a single anomaly check with no further bindings:

```clojure
(nom-test> [_ (operation-that-must-not-fail)])
```

See [error-handling.md](error-handling.md) for the broader
anomaly story.

### Test resources

Each brick that boots a system in tests has its own
`test-resources/<brick>/application-test.yml`. Shared test
configuration (common fixtures, common schemas) lives in the
`test-resources` brick.

The classpath URL pattern
`classpath:<brick>/application-test.yml` is what
`with-test-system` expects; load mechanics are covered by
[system-configurations.md](system-configurations.md).

### eftest synchronization

The test runner is eftest, which runs tests in parallel out of
process. Tests that boot expensive infrastructure
(testcontainers, the message bus, FDB) should be marked with
`^:eftest/synchronized` on the namespace to keep too many from
overwhelming CPU and memory:

```clojure
(ns ^:eftest/synchronized
  com.repldriven.mono.processor.interface-test
  ...)
```

Pure-function tests don't need the marker.

## Rules

**MUST:**

- Use `clojure.test/deftest` only for pure functions in
  isolation.
- Use the scenario runner for system-level tests.
- Manage system lifecycle in tests with `with-test-system`.
- Mark namespaces that boot infrastructure with
  `^:eftest/synchronized`.
- Place per-brick test config at
  `test-resources/<brick>/application-test.yml`.
- Use `nom-test>` for assertions over anomaly-returning calls.

**MUST NOT:**

- Use `use-fixtures` for system lifecycle.
- Write `deftest`-style integration tests against the command
  pipeline.
- Put projections inside production components — they live in
  `bank-test-projections`; the dependency arrow points
  test → production, never the reverse.
- Have the test model talk to FDB, the message bus, or any
  real infrastructure. The model is pure functions over a
  Clojure map.
- Compare full real-system state to full model state. Use
  targeted projections per assertion (`project-balances`,
  `project-account-statuses`, and so on).
- Skip the quiescence wait in the scenario runner. The CQRS
  read-side lags the write-side; without the explicit wait,
  tests flake in ways that look like real bugs.
- Enrich the model to make it "more realistic." Importing
  proto schemas, Malli contracts, or production component
  interfaces into `bank-test-model` defeats the property test.

## Discussion

The two-test-forms split keeps fast tests fast and slow tests
predictable. `deftest` for pure functions is cheap, parallel,
and doesn't need infrastructure. The scenario runner is
deliberately heavier — it boots real components — and is the
only place where system-level guarantees are established.

`with-test-system` over `use-fixtures` is a deliberate choice.
Fixtures encourage hidden global state, are surprisingly hard
to compose, and don't play well with anomaly-returning startup
code. Explicit `with-test-system` makes lifecycle visible at
every test, composes with `nom-test>`, and supports patch-fns
for handler injection cleanly.

The scenario "don'ts" are about preserving the property test's
ability to find bugs. If the model imports proto, Malli, or
production interfaces, the model and reality converge on the
same wrongness and the property stops finding bugs. If
projections leak into production code, the test concern leaks
into production. Both rules look pedantic; both pay off when
a real bug surfaces and the property finds it.

eftest synchronization is about resource starvation. Spinning
up ten testcontainers in parallel doesn't make tests faster —
it makes them flakier. The synchronized marker keeps
parallelism on cheap tests where it actually helps.

## References

- [ADR-0009](../adr/0009-model-equality-property-testing.md) —
  Model-equality property testing
- [docs/design/scenario-testing.md](../design/scenario-testing.md)
- [error-handling.md](error-handling.md)
- [system-configurations.md](system-configurations.md)
- `test-system` brick (provides `with-test-system`, `nom-test>`)
- `test-resources` brick (shared fixtures and schemas)
