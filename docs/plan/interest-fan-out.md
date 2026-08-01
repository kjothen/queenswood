# Plan: make interest runs reconcilable, then fan them out

## Context

[`account-serialisation.md`](account-serialisation.md) ends with a
section saying interest should fan out into per-account commands, and
defers the design. This is that design, and the conclusion is that the
fan-out is the second half of the work rather than the first.

Accrual and capitalisation dispatch one bank-wide command and iterate
accounts inside it. `run-interest` checks a daily-count policy limit,
pages every cash account in the bank through
`process-customer-accounts`, runs each account's posting in its own
short FDB transaction, and writes one `InterestRun` record at the end.

The per-account transaction boundary already exists. What does not
exist is any record of *which* accounts were done. A run that dies
halfway through leaves nothing behind: no run record, because that is
written last, and no per-account trace. The only recovery is to run the
whole bank again and rely on the posting being idempotent.

Fanning out without fixing that makes it worse, not better. One long
command at least fails visibly as one unit. A million independent
commands fail independently, and with no record of what was dispatched
there is nothing to reconcile them against.

## Decision

Establish the records first, populate them from today's loop, and only
then replace the loop with commands.

1. **Two records** — an evolved `InterestRun` describing the run's
   lifecycle, and a new `InterestAccountRun`, one row per account per
   run.
2. **Phase one** writes both from the existing loop. No commands, no
   keying, no bus. This buys crash resumption, progress, and
   reconciliation on its own.
3. **Phase two** replaces the loop with one keyed command per pending
   row. The per-account transaction is untouched.
4. **The names drop the cadence** — `accrue-day-interest` and
   `capitalize-accrued-interest`.

The staging is safe because the record shapes do not depend on the
trigger mechanism. Phase one writes the same rows the command version
will write, in the same transaction, and phase two changes only what
causes that transaction to run.

## Completion cannot be inferred, so it has to be recorded

`domain/daily-interest` returns `nil` when `interest-rate-bps` is zero.
`accrue-account` then writes no transaction and no carry, so an account
on a zero-rate product is legitimately complete and leaves no trace at
all.

That rules out every scheme that derives completion from evidence. A
reconciler checking "does this account have an accrual transaction for
this day" would report zero-rate accounts as outstanding forever and
re-dispatch them on every pass. Completion is a fact about the run, not
a fact recoverable from the ledger, so the run has to record it.

## The records

**`InterestRun`** — evolved from a done-marker into a lifecycle record.

- Primary key `[bank_id, business_day, kind]`. Today's `status` field
  is really the kind: its two values are `INTEREST_ACCRUE_DONE` and
  `INTEREST_CAPITALIZE_DONE`, which conflate what ran with whether it
  finished. Split them.
- `state`: `dispatched` when some rows failed, `closed` when none are
  left pending. `dispatching` is reserved for phase two, where
  enumeration spans more than one command.
- Count index on `[bank_id, kind, business_day]` — the same shape as
  before, so `check-daily-count` is unchanged. State stays out of the
  index because the record is written only once enumeration has
  finished, so every row it counts is already a real run. Phase two
  moves the write earlier to hold a cursor, and that is when state has
  to enter the index to keep a crashed run from blocking its own retry.
- `dispatch_cursor` belongs to phase two with it — a paging loop that
  lives inside one command has nothing to resume from.

**`InterestAccountRun`** — new, one row per account per run.

- Primary key `[bank_id, business_day, kind, account_id]`.
- `state`: `pending` → `done` | `failed`.
- Count indexes on `[bank_id, business_day, kind]` and
  `[bank_id, business_day, kind, state]`.

## Rows, not a counter

The obvious cheaper design is a `completed` counter on the run record,
incremented as accounts finish. FDB atomic adds do not join the
conflict set, so the contention argument against it fails — but the
correctness one does not. Command redelivery is at-least-once, so a
redelivered accrual increments the counter twice and the run reports
100% with work outstanding. Guarding that needs an idempotent
per-account marker, which is the row you were trying to avoid.

Rows plus a count index have the property for free: re-writing the same
primary key does not move a count. Redelivery is absorbed by the key.

That earns the cardinality, because one row then does four jobs:

- **It is the scope snapshot.** Written `pending` at enumeration, so
  the account set is fixed at dispatch and stays enumerable even as
  accounts open and close during the day.
- **It is the idempotency marker.** Flipped to `done` in the same FDB
  transaction as the posting, so the work and the record of the work
  commit together.
- **It is the progress source.** `count(state=done)` over
  `count(*)`, both O(1) off the index.
- **It is the gap index.** Outstanding work is a range scan of
  `state=pending` — you read the gap directly rather than diffing two
  sets.

The cardinality is also less than it first appears. A non-zero accrual
already writes a transaction and a balance carry for every account
every day. A tracking row is a third write on a path already doing two,
not a new order of magnitude.

## Dispatch and reconciliation are the same code path

Because outstanding work is a range scan of `pending` rows,
reconciliation is not a separate mechanism. It is the dispatcher run
again over rows that are still pending. There is no repair path to
build, test, or get wrong separately from the happy path.

This is what makes the crash story work. Writing a page of rows and
then dispatching that page is not atomic across the FDB/bus boundary,
so a crash in between leaves rows `pending` — which is exactly the
state reconciliation already handles. Self-healing by construction
rather than by remembering to handle it.

It also settles where the run record is written. `InterestRun` reaches
`dispatched` only when enumeration has finished, so a crash mid-fan-out
leaves it `dispatching` with a cursor. That is not a blocked re-run, it
is a resumable one, and because `check-daily-count` counts only
`dispatched` and `closed` rows the daily limit does not fire on the
retry. Writing the run before enumeration would invert this: the limit
would see a completed run and refuse the retry that recovers it.

A run closes when `count(pending)` reaches zero. If any rows are
`failed` it closes with a residue rather than hanging at 99.9%.

## Names describe the work, not the cadence

`scheduler/domain.clj`'s `task-allowed-periods` already says accrual is
`#{:scheduler-periodicity-daily}` while capitalisation is
`all-periods` — daily, monthly, or yearly, operator's choice. So
`capitalize-monthly-interest` names in the command the one thing the
system deliberately makes configurable, and a bank capitalising yearly
runs a command called "monthly".

Accrual is the opposite: daily by rule, and `domain/daily-interest`
computes exactly one day's worth. The day is the unit of computation,
not a cadence.

So name each for what it operates on. Accrual operates on a day of
interest; capitalisation operates on whatever has accrued.

- `accrue-daily-interest` → `accrue-day-interest`
- `capitalize-monthly-interest` → `capitalize-accrued-interest`
- `core/accrue-daily` → `core/accrue-day`
- `core/capitalize-monthly` → `core/capitalize-accrued`

After the split these name per-account work, which is what they will
actually be. The bank-scoped commands that replace them are
`start-interest-accrual` and `start-interest-capitalization`.

## The entry point stays in `interest`

Moving the bank-wide entry to `bank` or `cash-account` is tempting
because the scan is bank-wide, and it is the wrong move.

The set is not "all accounts in a bank". `customer-accounts` filters to
three product types and `:cash-account-status-opened`. That set is
defined by interest's rules, so bank-wide is the scope of the scan, not
the definition of the population.

The bookkeeping is interest's too. `InterestRun` and the
`check-daily-count` policy limit that reads its count index are what
answer "has this already run today", and splitting the dispatcher from
them puts the question in a different brick from its answer.

And the boundary runs the wrong way. Interest reaching out to
`cash-account-query` for reads is the allowed direction; `cash-account`
emitting interest commands would be one brick orchestrating another's
domain, which is what [ADR-0021](../adr/0021-changelog-relay.md) pushes
against.

What should move is the scan, not the entry. Interest pages every
account in the bank and filters client-side — a full scan per run,
twice a day. A filtered query on `cash-account-query` would let
interest keep the entry point while cash-account keeps ownership of how
accounts are found.

Worth noting for that query: `customer-product-types` is a hardcoded
allowlist, but the property that actually decides whether an account
earns interest is `interest-rate-bps` on the product version, which
`accrue-account` already reads. Deriving the set from the intrinsic
property would make the filtered query expressible without
interest-specific knowledge leaking into `cash-account`.

## Phase one: populate the records from today's loop

`process-customer-accounts` writes a page's `pending` rows in one
transaction as it enumerates, and each `accrue-account` /
`capitalize-account` flips its own row to `done` alongside the posting.
`InterestRun` gains its state field and is written `dispatching` at the
start of enumeration, `dispatched` at the end, `closed` when no rows
remain pending.

Error handling has to change with it. `process-customer-accounts`
currently does `(reduced result)` on the first anomaly, killing the
run. With rows, that leaves every subsequent account `pending` forever
and the run never closes. It has to mark the row `failed` and continue.
That is a real behaviour change — failures stop being immediate and
become a residue count — but it is the semantics fan-out forces anyway,
and it is easier to test inside the loop.

Phase one stands alone. Even if the fan-out never lands it buys crash
resumption where a dead run currently leaves no trace, progress that
can be watched, a nameable gap, and an audit answer for "was this
account considered on the 3rd".

## Phase two: fan out

The bank-scoped command becomes a dispatcher. It checks the daily
limit, enumerates the account set writing `pending` rows, emits one
command per row keyed on `account_id`, and marks the run `dispatched`.
The per-account processor does what `accrue-account` does today,
including the `done` flip, unchanged.

At that point interest joins per-account ordering and stops being the
exception to the serialisation `account-serialisation.md` describes.

## What this needs

- A proto message for `InterestAccountRun` under
  `components/schema/resources/schemas/interest/`, added to
  `schema.proto`, and a record-type entry in
  `components/resources/resources/system/fdb-record-types.yml`. The
  scenario rig `!include`s that file rather than copying it, so this is
  one edit.
- `InterestRun`'s proto gains `state` and `closed_at`, and its `status`
  enum splits into a kind and a state.
- Renamed Avro schemas under
  `components/schema/resources/schemas/interest/`, remapped in
  `components/resources/resources/system/avro-schemas.yml` — the single
  registry, which `avro.yml` includes.
- The two call sites of the renamed functions: `scheduler/core.clj`'s
  `task-registry` and `api/simulate/handlers.clj`.
- Phase two adds an `interest` command topic keyed per account, and the
  dispatch site sets `{:key account-id}` explicitly, per the
  declared-never-inferred rule in `account-serialisation.md`.

## Scope and caveats

- **The scheduler calls interest synchronously.**
  `scheduler/core.clj`'s `task-registry` invokes
  `interest/accrue-daily` directly and uses its return value; this path
  never touches the bus. After phase two that call can only report
  "dispatched N", not "processed N", and a green scheduled job stops
  meaning the work finished. That is a product decision as much as a
  technical one, and it is the largest hidden cost in this plan.
- **The index half of the redelivery property is already proved; the
  bus half is not.** Rows were chosen over a counter because a re-write
  of the same key does not move a count index. Phase one exercises that
  without needing a redelivery: every row is written twice per run,
  `pending` at enumeration and `done` beside the posting, so the
  scenario asserting `scope 2` against two accounts — rather than 4 —
  is the proof. What stays untested is the path, since nothing
  redelivers until the commands land.
- **Phase one buys reconciliation, not serialisation.** Keying and
  per-account ordering arrive only with the commands.
- **Whether zero-rate accounts get a row is open.** They could be
  excluded at enumeration, since the dispatcher reads the product
  version anyway, which shrinks both the fan-out and the storage.
  Including them is probably right for a financial process — "this
  account was considered and earned nothing" is a better answer than
  silence — but the cost is real if many accounts sit on zero-rate
  products.
- **Retention is unsolved.** One row per account per day per kind is
  730 rows per account per year. These are operational rather than
  financial records — the transactions are the financial ones — so they
  should age out with closed runs. That job is not designed.
- **The deadline that flips stale `pending` to `failed` is a policy
  number nobody has picked.** Without it a run with permanently failing
  accounts never closes.
- **The dispatcher is still O(N) inside one command.** Fan-out moves
  the bottleneck rather than removing it: emitting a million commands
  in one handler has the same no-progress, no-backpressure problem the
  fan-out is meant to solve. It likely has to page and chain to itself,
  which is what `dispatch_cursor` is for, but that interaction is not
  worked through here.

## Rules that cost real time to learn

- **A proto2 `required` field holding its default value fails to
  parse.** protojure drops zero, false, and empty defaults on the wire,
  and the Java FDB parse then rejects the record as missing a required
  field. `InterestAccountRun`'s counters and any enum whose first
  meaningful value could be zero must be `optional`.
- **Renaming or adding a proto enum label needs a forced prep.** Run
  `clj -X:deps prep :aliases '[:dev]'` with `:force true` after
  splitting `InterestRunStatus`, or full-system startup fails
  serialising against the stale generated class while brick tests still
  pass.
- **The progress poll has to read at SNAPSHOT.** A count index read at
  SERIALIZABLE joins the read-conflict set, so polling progress would
  conflict with every concurrent accrual in the bank and re-serialise
  the whole run. This is the same trap `account-serialisation.md`
  documents for the payment limit, reached from the opposite direction.
- **`0` is truthy in Clojure, and this code already depends on it.**
  `accrue-account` guards its save on the transaction value rather than
  on `whole-units` because `daily-interest` can return `whole-units 0`
  with a carry. The same care applies to any `pending`/`done` counting
  built on top.
