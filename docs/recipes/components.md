# Components

## Problem

You want to add or modify a Polylith component.

## Solution

We organise components in `components/` with a small public
surface — `interface.clj` — that delegates to internal namespaces.
Other code only ever touches the interface; the implementation is
private to the component.

### File layout

```
components/<brick>/
  src/com/repldriven/mono/<brick>/
    interface.clj    ; public API, delegates to other namespaces
    core.clj         ; primary implementation
    domain.clj       ; (often) data shapes, validation
    store.clj        ; (often) persistence
    system.clj       ; (when registering system components)
  deps.edn
```

`interface.clj` re-exports public functions:

```clojure
(ns com.repldriven.mono.<brick>.interface
  (:require [com.repldriven.mono.<brick>.core :as core]))

(defn do-the-thing [x] (core/do-the-thing x))
```

If your component contributes to a system definition, see
[system-components.md](system-components.md) for the
registration patterns.

### Accessing other components

A component reaches another's API through its `interface.clj`,
never through internal namespaces:

```clojure
;; OK
[com.repldriven.mono.error.interface :as error]

;; Not OK
[com.repldriven.mono.error.core :as error-core]
```

Don't add other components to `deps.edn`. Polylith resolves
inter-component dependencies through interface namespace
references in source code. Only third-party libraries belong in
a brick's `deps.edn`.

## Rules

**MUST:**

- Components define their public API in `interface.clj`.
- `interface.clj` delegates to other namespaces in the same
  component.
- Other components are accessed via their `interface.clj`.

**MUST NOT:**

- Implement logic directly in `interface.clj`.
- Reach into another component's internal namespaces.
- Include other components in `deps.edn`.

## Discussion

The brick boundary is the project's only mechanism for hiding
implementation. If components reach across `interface.clj` or pull
each other in via `deps.edn`, the boundary stops meaning anything
and refactors get painful. The rules look pedantic; they pay off
when you can change a brick's internals freely because no caller
depended on them.

The split between `interface.clj` (delegates) and the rest
(implements) lets you reorganise internals without rewriting any
of the callers. The cost is one more level of indirection in the
function-call graph. We accept that cost.

## References

- [ADR-0001](../adr/0001-reuse-mono-as-upstream.md) — Reuse mono as upstream
- [ADR-0011](../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library
- [Polylith documentation](https://polylith.gitbook.io/polylith)
