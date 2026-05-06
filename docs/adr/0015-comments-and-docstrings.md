# 15. Comments and docstrings

## Status

Accepted.

## Context

Code comments and docstrings drift in two directions over time. They
either accumulate as load-bearing context (the *why* a function looks
the way it does — a bug it works around, an invariant it preserves,
a constraint imposed by an upstream library) or they decay into
restatement of the code itself (the *what* — "increment counter",
"call save-record then save-legs"). The first is valuable; the
second is noise that ages worse than the code beside it, since the
code stays correct under refactor while the comment doesn't.

Without a stated rule, we end up with both kinds, and the second
kind crowds out the first. Multi-paragraph explanatory blocks in
front of two-line functions stop being read; rationale that *is*
load-bearing gets lost in the wash. Reviewers also can't tell
whether a comment is required by the codebase's conventions or just
an author's habit.

The Polylith brick model gives us a natural anchor: every brick has
an `interface.clj`. That namespace is the only file external
callers should look at to understand what the brick does. So the
documentation effort concentrates there; supporting code is
expected to be self-explanatory under the names it picks.

## Decision

**Docstrings are the primary form of commentary.** Inline `;;`
comments are exceptional.

### Component interface namespace

Every `interface.clj` opens with a one-paragraph ns docstring that
says what the brick is for and what it returns to its callers.
Two or three sentences. No implementation detail; no list of public
fns (the file already lists them).

```clojure
(ns com.repldriven.mono.fdb.interface
  "FoundationDB Record Layer wrapper. Exposes record-store
  open/save/load/scan plus a `transact` macro that runs a body
  inside a single FDB transaction. Component-kinds for
  cluster-file-path, db, record-db, store, meta-store, and
  changelog watchers are registered via this brick's `system`
  namespace.")
```

### Public functions

Docstrings live on the **`interface.clj` re-export**, not on the
implementation. The interface is the single contract surface;
readers shouldn't have to chase into `core.clj` / `components.clj`
to find the contract. Implementation defs stay bare.

A one-line purpose, then an `Args:` block that names each param in
one phrase. No "how it works"; the body is the how.

```clojure
;; components/fdb/src/.../fdb/interface.clj
(defn save-record
  "Persist a record into the given store, returning the saved
  record or an anomaly.

  Args:
  - store: an open FDBRecordStore.
  - record: a protobuf message matching the store's schema."
  [store record]
  (record/save-record store record))

;; components/fdb/src/.../fdb/record.clj — bare, no docstring.
(defn save-record [store record]
  …)
```

For `(def name impl/name)` re-exports, attach the docstring as
metadata so `(doc name)` and tooling pick it up:

```clojure
(def ^{:doc "..."} required-component core/required-component)
```

Acceptable to add one extra short paragraph if there's a non-obvious
contract worth pinning (return-on-anomaly behaviour, idempotency,
side effects). Stop at one paragraph.

### Private functions and non-interface implementation files

Default: no docstring, no comments. The name is the description.
The implementation file says only what the code does — no prose
about what the brick is for, no per-fn purpose blocks, no usage
examples. All of that lives on `interface.clj`.

Only add a docstring when the fn's role inside its own ns isn't
obvious from the name — which usually means the name should change
first.

### Inline `;;` comments

Default: none. The bar for adding one is *non-obvious WHY*. A
hidden invariant, a workaround for an upstream bug, a behaviour
that would surprise a reader who's only seen the surrounding code.

```clojure
;; Pulsar's createMissedPartitions has been observed to silently
;; no-op on a single-process dev broker; audit afterwards.
(.createMissedPartitions topics topic-name)
```

Not acceptable: comments restating the code, comments narrating the
control flow, comments with multi-paragraph backstory. If the
rationale is multi-paragraph, it belongs in an ADR or a recipe, not
in line.

### What to delete

When trimming an existing file:

- Multi-paragraph block comments above a function → keep one
  sentence in the docstring if it captures a non-obvious WHY;
  delete the rest.
- Inline notes describing what the next form does → delete.
- Inline notes referencing the current change/PR/incident
  ("added for the X flow", "fixes issue #123") → delete; that
  context belongs in the commit message and ages out of the file.
- Comments that just restate types or shapes already enforced by
  `:system/config-schema` / spec / malli → delete.

## Consequences

Easier:

- Reviewers know the bar without asking. A multi-paragraph block
  comment in a PR diff is a flag, not a default.
- The interface.clj of any brick is the canonical "what does this
  do" surface. Operators and contributors have one place to look.
- Less rotting prose to maintain through refactors. When the code
  changes, the comment doesn't have to be revisited because there
  rarely is one.

Harder:

- Naming has to carry more weight. A function that *needs* a
  comment to be understood is a hint that the name is wrong; the
  rule turns "add a comment" into "rename the fn".
- Genuinely surprising upstream behaviour (Pulsar quirks, FDB
  Record Layer edge cases) does need to be captured somewhere.
  Inline comments are the right place when they're terse;
  longer-lived rationale moves to ADRs or recipes.
- Picking up the convention is a habit shift for contributors used
  to a "comment everything" style. Reviewers will need to push
  back on overcommented PRs for a while.

Inherited from `mono` (ADR-0001) only loosely; this is a
Queenswood-specific tightening.
