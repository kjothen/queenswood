# Code style

## Problem

You want to write Clojure that fits the project's conventions.

## Solution

We use zprint for formatting, clj-kondo for linting, and a
small set of project-specific conventions for namespaces,
naming, destructuring, and a few common pitfalls.

### Formatting

zprint formats all Clojure source. Configuration is in
`.zprint.edn` at the workspace root.

- Line width: **80 characters.**
- The pre-commit hook auto-formats staged files; you don't
  normally run zprint manually.
- zprint does not reflow string content, so multi-line
  docstrings need manual wrapping at 80:

```clojure
(defn my-fn
  "First line, kept within 80 chars.

  Further detail on subsequent lines, also wrapped at 80. Use
  blank lines to separate paragraphs."
  [args]
  body)
```

### Namespace requires

Order `:require` entries innermost to outermost, separated by
blank lines:

1. **Extension namespaces** — bare requires whose only purpose
  is to extend multimethods (system-component registrations).
  These come first because loading them establishes the
  dispatch surface for everything below.
2. **Internal namespaces** — other files in the same brick.
3. **Other component interfaces** — `interface.clj` files of
  other bricks. Interfaces only.
4. **External libraries.**
5. **Standard libraries.**

Bare requires use the bracketed unaliased form. The earlier
convention used unbracketed namespaces; we walked that back.
See [system-components.md](system-components.md) for the test
bundling pattern.

```clojure
(ns ^:eftest/synchronized
  com.repldriven.mono.processor.interface-test
  (:require
    [com.repldriven.mono.testcontainers.interface]   ; bare

    [com.repldriven.mono.processor.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface
     :refer [with-test-system nom-test>]]
    [com.repldriven.mono.db.interface :as sql]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.json.interface :as json]

    [clojure.test :refer [deftest is testing]]))
```

In **component interface tests**, alias the SUT (system under
test) as `SUT`, and require no other namespaces from the same
component.

### Naming

Names are narrow. A function in `command` is `process` or
`send`, not `process-command` or `send-command`. The brick name
is the context; functions don't repeat it. Thread macros help
avoid naming intermediate values when the chain is the
meaningful thing.

Side-effecting functions don't take a `!` suffix. Names
describe what a function returns or what effect it has, not
whether it causes one.

Reference: Zachary Tellman's *Elements of Clojure* on naming —
*"if a function crosses data scope boundaries, there should be
a verb in the name. If it pulls data from another scope, it
should describe the datatype it returns. If it pushes data
into another scope, it should describe the effect it has."*

### Anonymous functions

Use `(fn [x] ...)` for anonymous functions. Avoid the `#(...)`
reader macro form.

```clojure
;; OK
(map (fn [x] (* x 2)) xs)

;; Not OK
(map #(* % 2) xs)
```

### Conditional threading

For `cond->` and `cond->>`, put each predicate and its action
on separate consecutive lines, with a blank line between pairs:

```clojure
(cond-> initial-value
  pred1
  (action1)

  pred2
  (action2))
```

The pattern reads as a sequence of "when this, do that" steps
and keeps each pair visually distinct.

### Destructuring and bindings

Destructure one level at a time inside `let`, not nested in
function arguments. Take the full value as a plain argument
and bind each level explicitly:

```clojure
;; OK
(defn create
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [body path]} parameters
        {:keys [project-id]} path
        {:strs [account-id service-account]} body
        {:strs [display-name description]} service-account]
    ...))

;; Not OK — nested destructuring in arguments
(defn create
  [{{{:keys [project-id]} :path
     {:strs [account-id]} :body} :parameters}]
  ...)
```

Prefer destructuring over `get` / `get-in` chains. The
destructured form makes the data shape obvious.

When writing `let` bindings, keep each binding's value on the
same line as its name. Let zprint do any wrapping; only wrap
manually if a binding clearly exceeds 80 chars.

```clojure
;; OK — single line per binding; zprint wraps if needed
(let [account (store/get bank id)
      balance (balance/available bank id)]
  ...)

;; Not OK — pre-wrapped; zprint will not reflow it
(let [account
      (store/get bank id)
      balance
      (balance/available bank id)]
  ...)
```

This applies to *any* form inside `[]` — `let` bindings,
argument vectors, destructuring, literal vectors. zprint
treats manual formatting inside `[]` as deliberate and won't
reflow it, so premature wrapping permanently defeats the
formatter.

### Generating IDs

Use `util/uuidv7` from the `utility` brick — not
`random-uuid`. UUIDv7 is time-ordered, which gives FDB and
other sorted stores better index locality and lets records
sort chronologically for free.

```clojure
(:require [com.repldriven.mono.utility.interface :as util])

;; OK
(util/uuidv7)

;; Not OK
(random-uuid)
```

### Recording timestamps

Use `util/now` from the `utility` brick — not
`(System/currentTimeMillis)`, `(Instant/now)`, or other
platform clock APIs directly. `util/now` returns epoch
milliseconds and is the project's single canonical clock seam,
which keeps tests mockable and behaviour consistent across the
codebase. For RFC 3339 strings, use `util/now-rfc3339`.

```clojure
(:require [com.repldriven.mono.utility.interface :as util])

;; OK
(util/now)
(util/now-rfc3339)

;; Not OK
(System/currentTimeMillis)
(java.time.Instant/now)
```

### Linting

clj-kondo is configured in `.clj-kondo/config.edn`. The
pre-commit hook runs it against the full workspace before
allowing a commit. Custom `:lint-as` mappings let kondo treat
project macros as their core equivalents —
`error/let-nom>` lints as `clojure.core/let`, `error/nom->`
lints as `clojure.core/->`, and so on.

## Rules

**MUST:**

- All Clojure source is formatted with zprint (80-column).
- Docstrings are manually wrapped at 80.
- `:require` entries are ordered innermost-to-outermost
  (extension namespaces, internal, other component
  interfaces, external libraries, standard libraries) with
  blank lines between groups.
- Bare requires use the bracketed unaliased form.
- Component interface tests alias the SUT as `SUT` and
  require no other namespaces from the same component.
- Use `util/uuidv7` for new IDs.
- Use `util/now` for current-time reads (and `util/now-rfc3339`
  for RFC 3339 strings).
- Anonymous functions use `(fn [x] ...)`.
- Destructure one level at a time in `let`.

**MUST NOT:**

- Use `!` suffix on side-effecting function names.
- Use the `#(...)` reader macro for anonymous functions.
- Nest destructuring in function arguments.
- Use `random-uuid` for new IDs.
- Use `(System/currentTimeMillis)`, `(Instant/now)`, or other
  platform clock APIs directly. Go through `util/now`.
- Repeat the brick name in function names within that brick
  (`process-command` in `command`, `send-account` in
  `bank-cash-account`, and so on).

**SHOULD:**

- Prefer destructuring over `get` / `get-in` chains.
- Prefer thread macros (`->`, `->>`, `error/nom->`) over
  intermediate `let` bindings when the chain is the
  meaningful value.
- Format `cond->` / `cond->>` with each predicate and action on
  separate lines, blank lines between pairs.
- Aim for referential transparency — pure functions named
  after what they return — except at clear effect boundaries.

## Discussion

The naming rule is the hardest to internalise and pays off
the most. Compound names like `process-command` or
`send-account` read fluently in isolation but are noisy at
every call site. The brick name is already part of the
namespace; repeating it in the function name doubles the
noise without adding meaning.

The `(fn ...)` over `#(...)` rule is about composition.
`#(f %)` and `#(g %1 %2)` are fine in trivial cases, but the
moment you nest or mix arities the form gets ambiguous fast.
Standard `(fn ...)` is always clear.

The destructure-in-let rule is about readable diffs and
keeping cognitive distance short. Nested argument
destructuring puts every concern on the function's signature
line, where a small change touches a lot of layout.
Destructuring in `let` keeps the function signature stable
and makes each level a single-line decision.

The UUIDv7 rule is performance and ordering: time-ordered IDs
give FDB and any other sorted store better locality and
chronological sort for free.

The `util/now` rule is about having a single clock seam.
Reaching for `System/currentTimeMillis` or `Instant/now`
directly scatters the platform dependency through the codebase
and makes time-based behaviour harder to mock in tests. One
wrapper, one place to swap.

## References

- [ADR-0006](../adr/0006-kebab-case-keyword-keys.md) — Kebab-case keyword keys
- [ADR-0012](../adr/0012-pre-commit-hooks.md) — Pre-commit hooks
- [error-handling.md](error-handling.md)
- [system-components.md](system-components.md)
- *Elements of Clojure* by Zachary Tellman
