# Plan: classify FDB failures at the transaction boundary

## Context

`fdb/transact` catches every `Exception` the Record Layer throws and
funnels it into a single anomaly:

```clojure
(catch Exception e
  (or (::anomaly (ex-data e))
      (error/fail category {:message message :exception e :stack-trace ...})))
```

`category` is supplied by the caller — `:user/save`,
`:cash-account/get-by-bban`, `:payment/save-outbound-payment` and so on
— defaulting to `:fdb/transact` only in the two-arity form. Almost
every call site supplies one.

The result is that a retry-exhausted write conflict, a transaction over
FDB's size cap, and a cluster stalled behind its ratekeeper are
indistinguishable to every caller. `api`'s `error-response` sets
`:type (str (error/kind anomaly))`, and `anomaly->status` sends any
non-rejection anomaly to 500. So a contended save surfaces as:

```json
{"title": "FAILED", "type": ":user/save", "status": 500}
```

That is worse than uninformative. It reads as though saving the user
was rejected on its merits, when the operation was well-formed and the
storage layer was busy. A client cannot tell that retrying would
probably succeed.

**The classification already exists in the Record Layer's type
hierarchy** — Queenswood is discarding it rather than lacking it:

```
RecordCoreException
├── RecordCoreRetriableTransactionException     retrying may help
│   ├── FDBStoreRetriableException
│   │   ├── FDBStoreTransactionConflictException    not_committed
│   │   └── FDBStoreTransactionIsTooOldException    past_version, 5s cap
│   └── FDBStoreLockTakenException
└── RecordCoreStorageException
    └── FDBStoreException                       retrying will not help
        ├── FDBStoreTransactionTimeoutException     timed_out
        ├── FDBStoreTransactionSizeException        transaction over cap
        ├── FDBStoreKeySizeException
        └── FDBStoreValueSizeException
```

`LoggableTimeoutException` sits outside that tree. It is raised when an
`asyncToSync` deadline expires, and every aggregate read now passes
through `asyncToSync`, so it is reachable on the read paths as well as
the write ones.

## Decision

Classify inside `transact`, so the distinction reaches the API's
problem `type` without any caller writing an `instance?` check.

```clojure
(def ^:private exception->kind
  [[LoggableTimeoutException                          :fdb/timeout]
   [FDBExceptions$FDBStoreTransactionTimeoutException :fdb/timeout]
   [RecordCoreRetriableTransactionException           :fdb/contention]])

(defn- classify
  [^Throwable e fallback]
  (let [root (loop [t e] (if-let [c (.getCause t)] (recur c) t))]
    (or (some (fn [[klass kind]] (when (instance? klass root) kind))
              exception->kind)
        fallback)))
```

Order is most-specific-first; the hierarchy does the grouping, so
`:fdb/contention` covers conflict, too-old and lock-taken without
enumerating them.

The size exceptions are deliberately unclassified. They indicate a bug
or a genuinely oversized write, a caller can do nothing differently,
and the generic anomaly carries a stack trace already.

## The caller's category becomes payload, not kind

The obstacle is that callers supply a category and would appear to lose
it. Two measurements resolve this.

**Nothing consumes those categories.** Each appears exactly once in the
workspace, at its own `transact` call site. None is matched on,
dispatched on, or listed in `api`'s `rejection-status-overrides`. Their
only observable effect is the `type` string on a 500 and a line in a
log. There is no contract to break.

**They are context, not kind.** `:cash-account/get-by-bban` does not
describe a kind of problem; it describes the operation in progress. It
occupies the kind slot because there was nowhere else to put it. So
move it rather than choose between them:

```clojure
(error/fail (classify e category)
            {:message message
             :operation category
             :exception e
             :stack-trace ...})
```

The kind becomes accurate, the operation survives as payload, and no
caller loses information it was using.

The tempting alternative — classify only when `category` is the default
`:fdb/transact` — fails on the same measurement. That path covers a
single call site, so classification would be invisible everywhere it
matters.

## `api`: status for the new kinds

These are error anomalies, not rejections: they are not the caller's
fault, and they must not flow through `rejection-kind->status`. Add a
table beside the existing one in `api/errors.clj`:

```clojure
(def ^:private error-status-overrides
  {:fdb/contention 503
   :fdb/timeout 504})
```

and branch on it in `anomaly->status` **before** the blanket
`(not (error/rejection? anomaly)) -> 500`, or it will never be reached.

503 for contention says retries were exhausted and a later attempt may
succeed, which pairs with `Retry-After`. 504 for timeout says the
storage layer did not answer in time. Treating both as 503 and letting
`type` carry the distinction is a defensible alternative.

## What this needs

- `fdb/transact.clj`: `exception->kind`, `classify`, and the changed
  `error/fail` call.
- `api/errors.clj`: `error-status-overrides` plus the branch in
  `anomaly->status`.
- OpenAPI: 503 and 504 documented on every operation that writes or
  reads through FDB, with examples. Per
  [ADR-0014](../adr/0014-openapi-3x-compliance.md) every response shape
  is a documented, referenced component, so this is the bulk of the
  work rather than the classification itself.
- Tests: a unit test per classified exception shape, including one
  nested inside a wrapper, since `.run` wraps what it rethrows.

## What implementation corrected

Three of the predictions above were wrong, in ways worth keeping.

**`category` reaches an anomaly by three paths, not one.** `try-nom`
catches *any* Exception and converts it to an anomaly with `category` as
the kind, so an exception raised inside `f` never reaches the outer
`catch` this plan targeted — it becomes an anomaly, gets wrapped in
`ex-info` to force the rollback, and is unwrapped again on the way out.
The aggregate reads are called inside `f`, so the `asyncToSync`
timeouts would have been missed entirely. Classification therefore
happens in a `reclassify` applied where an anomaly leaves `transact`,
which covers all three paths and leaves a domain rejection — no
`:exception` in its payload — untouched.

**Walking to the root cause is wrong.** The Record Layer's own types
wrap the FDB error they describe, so `LoggableTimeoutException` and
`FDBStoreTransactionConflictException` both sit *above* their cause. A
root-cause walk steps past the very type being matched. The chain is
scanned at every level instead, outermost first, as the most proximate
description. `check.clj`'s `meta-data-already-current?` had the same
latent defect and was corrected with it.

**The OpenAPI work was two lines, not the bulk.** Every `/v1` route
inherits one shared `:responses` map, so 503 and 504 reached all
operations at once, with two example components alongside the existing
ones.

## Decisions and risks

**The require cycle constrains placement.** `fdb/check.clj` imports the
`Txn` class from `fdb/transact.clj`, so `transact` cannot require
`check`. `classify` therefore lives in `transact.clj`, and its
root-cause walk duplicates the one in `check.clj`. Three lines each.
Acceptable initially; a shared namespace is the fix if a third caller
appears.

**`:operation` is a new payload key.** Check whether `error/payload`
already has a convention for naming the operation in progress, so this
does not become a one-off.

**The `type` string changes for every write path** when FDB is the
cause. Nothing consumes it programmatically, but anything watching logs
or dashboards for `:user/save` and similar will see `:fdb/contention`
instead.

**It invites a larger question.** Once the category is payload rather
than kind, "should `transact` take a category at all, or just a
message?" becomes fair to ask. That is a separate change and should not
be folded in.

## Not in scope

`check.clj` gains no `retriable?` or `timeout?` predicate. Once
`transact` classifies, a caller reads the anomaly kind rather than
reaching past it to the exception, so those predicates would have no
consumer. See [ADR-0005](../adr/0005-error-handling-with-anomalies.md)
for why anomalies rather than exceptions cross a brick boundary.
