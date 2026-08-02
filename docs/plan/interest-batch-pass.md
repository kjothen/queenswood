# Plan: accrue interest as one batch pass

## Context

Every step of this plan has landed. An interest run writes an
`InterestRun` lifecycle record and one `InterestAccountRun` row per
account; neither pass posts a control leg per account; both aggregate
the bank's side at close; accrual computes and writes entirely from
what the scan froze; and `Balance` is keyed from `bank_id`, so the
pass scans one bank rather than all of them. What follows is kept as
the record of why it is shaped this way.

The second half proposed replacing the enumeration loop with one keyed
command per account. It should not be built, and this document replaces
it. Two things came out of costing it.

**The fan-out would have contended on two rows.** `balance/core.clj`'s
`apply-legs` loads every affected account's balances, computes, and
writes the records back — a read-modify-write inside a serialisable
transaction. `ledger-account/core.clj`'s `add-control-legs` attaches a
2400 interest-payable control leg to the customer's accrued leg, and the
accrual's other leg is 5100 interest expense. Both are bank-level rows
shared by every account in the bank, so every accrual read-modify-writes
the same two records. The current loop hides this by never being
concurrent. A million keyed commands would serialise on those two rows
whatever the account key says, and pay retries on top — slower than the
loop they replace.

**The work itself is one multiply per balance.** A day's accrual is the
balance times a rate the product version fixes, plus the carry. There
are few product versions, so the rate map is small enough to hold. The
cost is reading and writing the records, not computing over them, and
neither of those wants a bus.

## Decision

Accrue in one chunked pass per bank, and post the general ledger once.

1. **Accrual stops being a customer-visible transaction.** The daily
   pass moves an internal bucket. What the customer sees is
   capitalisation.
2. **`Balance` gains `bank_id` at the head of its primary key**, so it
   scans in the same order as `CashAccount`.
3. **One pass merge-joins the two scans in Clojure**, multiplies, and
   writes back in chunks.
4. **The general ledger gets one entry per bank per run** instead of a
   control leg per account.
5. **The per-account row becomes the idempotency guard and the sum
   source**, and gains the amount and the balance it computed on.
6. **Recovery is re-running the pass, not resuming it.** A pass this
   short does not need resumption machinery.

## Accrual is not a statement line

Customers care about accrued interest landing in their account, and
that is capitalisation. Daily accrual credits an internal
interest-accrued bucket that nothing outside the bank reads until the
sweep.

So the daily pass writes two records per balance: the customer's
interest-accrued balance, carrying the sub-unit remainder in
`credit_carry` as it does today, and the `InterestAccountRun` row. No
`Transaction`, no `TransactionLeg`, no control legs. That is the write
amplification gone — one write per balance rather than five to ten per
account — and the hot GL rows are no longer touched per account at all.

Capitalisation keeps its per-account transaction, because that
transaction *is* the statement line. It drops its control legs the same
way, and its ledger side aggregates the same way.

## The pass

The FDB Record Layer has no join operator — its planner works over one
record type at a time — so this is Clojure over two paged scans, not a
query the database plans.

`CashAccount`'s primary key is `[bank_id, account_id]`. Once `Balance`
is keyed `[bank_id, account_id, balance_type, currency,
balance_status]`, both scan under the same leading prefix and in the
same order, so the pass advances two cursors in lockstep and pairs each
account with its balances as they stream. No random lookups, no join
index.

**The rate map cannot be derived from the live products.** An account
pins `product_id` and `version_id` when it opens, and a pinned version
may be one the bank no longer offers, so enumerating current versions
would miss accounts. Collect the distinct pairs from the accounts as
they stream and memoise them for the run — the same lookup
`interest/core.clj` already caches, without the TTL.

**Chunk the writes.** FDB caps a transaction at 10MB and five seconds,
so the pass commits every N accounts rather than at the end. The
balance update and its row go in the same chunk transaction, so a chunk
either lands whole or not at all.

## What the scan freezes, and the one row the pass writes

Pairing the two scans is not the point on its own. The point is that
the scan *freezes* every input an accrual needs, so the write that
follows needs no read at all.

Three values come off the scan, and none is read again:

- the account's available balance, the principal interest accrues on,
- the sub-unit remainder carried from the previous day,
- the running total already in the interest-accrued bucket.

**The principal is the available balance, not the posted one.** Money
reserved against a pending outgoing payment is already committed, so
it stops earning when the reservation is taken rather than when it
settles; money still pending inbound has not arrived, so it has not
started earning. That is a computation over several buckets rather
than a figure on one, which is another thing the merged scan pays
for — every bucket the sum needs is already in hand, so what would
otherwise be a second read is arithmetic. It uses
`balance-domain/available-balance`, the same definition the limit
checks use, so there is one meaning of available in the system.

From those the pass computes the day's whole units and the new
remainder, and writes one balance row: the interest-accrued bucket,
its credit advanced and its carry replaced.

**The pass never creates that row.** An account it processes is
guaranteed to have one, because the product templates that declare an
interest-accrued posted bucket — current, savings, term deposit — are
exactly the product types the pass admits, and own funds, which
declares none, is exactly the type it filters out. The bucket is
opened with the account. So a missing row is a misconfigured product
or a corrupted account. The pass says so — it logs that the account
has a rate but nowhere to accrue to — and records the account done at
zero. It does not fail it: the account has done nothing wrong, and a
bank left short by a template should not have its accounts marked
failed over it. What it must not do is read the absence as a zero
balance and accrue silently against a figure nobody wrote.

A product at zero bps could in principle be opened without an accrued
bucket, and then the invariant would need qualifying. It is not worth
doing: the bucket costs one empty row, the rate is a property of the
version rather than the account, and a rate that moves off zero would
have to backfill every account already opened.

**That write is safe unread because nothing else writes that row.**
Two things in the workspace touch an interest-accrued bucket: this
accrual, which credits it, and capitalisation, which sweeps it. The
API reads it and no more. The pass is single-writer per bank and
`check-daily-count` makes a second concurrent pass a rejection, so the
frozen total cannot go stale underneath the write.

The default balance is a different matter, and the distinction is the
design. Payments move it continuously, so a read-modify-write against
it from a value read in an earlier transaction would drop concurrent
credits. The pass therefore never writes it. It reads it once, at the
scan, and accrues on the balance as of that moment — a business choice
already taken rather than a concession to the mechanism.

### The carry has to move first

Today the remainder lives on the default posted row: `daily-interest`
takes `credit_carry` from the default bucket, and `set-carry` writes
the new one back there. That is the only reason the accrual path
touches the contended row at all, and it is what forces it through
`apply-legs` — which then re-reads the account's policies and every
balance it has, inside the posting transaction, to perform a
read-modify-write the pass never needed.

So `credit_carry` moves to the interest-accrued row, where the rest of
the accrual state already sits. Every `Balance` carries the field
already, so this is not a schema change — only a change of which row
is read from and written to.

Remainders currently held on default rows are discarded rather than
migrated. They are sub-unit amounts and nothing is live, so the cost
is a rounding error on a system with no customers.

### Accrual leaves the leg-posting path

Writing the row directly means accrual no longer goes through
`balance/apply-legs`, and so is no longer policy-checked. That is
intended rather than incidental: the accrued bucket is not spendable,
and a limit on interest belongs at capitalisation, when the money
becomes reachable. Capitalisation keeps its transaction and keeps
going through `apply-legs`.

What this removes from each account is the policy resolution, the scan
of the account's balances, and the point read behind `set-carry` — and
it collapses the two balance writes, one for the credit and one for
the carry, into the single row write above.

## Why the rate is not denormalised onto the account

Carrying a precomputed daily rate on `CashAccount` was considered, to
save the product lookup. It is rejected on two counts.

**A rate is not necessarily a scalar.** A tiered schedule — 5% on the
first 10,000 and 3% above it, say — is a function of the balance, and
the balance is what the pass is scanning. There is no single field to
denormalise, because the input to the rate is not known until the row
is read.

**Precomputing a daily figure loses money.** `daily-interest`
multiplies by the balance before dividing by 365, and that order is
load-bearing. Dividing first gives `bps * 100 / 365`, which at 100 bps
is `10000/365` = 27.397 truncated to 27 — a 1.4% error on every
account, baked into stored data. The 365 also encodes the day-count
convention, so storing it turns a change of convention into a data
migration.

None of this costs anything, because the lookup was never the expense.
The memoised map holds one entry per distinct `(product_id,
version_id)` a bank has in use — dozens, not millions — so after the
first few accounts it is a hash lookup, and a schedule costs two or
three multiplies instead of one. The pass does not care how the rate is
derived, only that it is a pure function of the balance and the
product version, so a schedule drops in later without reshaping
anything here.

Two things to get right when it does. Accumulate the bands in
micro-units and divide **once** — computing each band separately and
summing gives every band its own rounding and drifts the carry, where
one division keeps the single rounding step the current code has. And
which balance selects the band is a product decision: today's balance,
average daily balance, and minimum balance over the period give
different answers, and daily accrual on the current balance is merely
the one that falls out by default.

## What the scan primitive gives, and what it does not

`fdb/scan-records` takes a `:prefix` of leading primary-key parts and a
cursor, and the cursor is a *single* key element — the one at the
position immediately after the prefix — applied as an exclusive bound.

That is unproblematic for `CashAccount`, where `account_id` is unique
under `bank_id`. For `Balance`, where several rows share one
`account_id`, taking the returned cursor at face value loses records.
`TupleRange.toRange` runs an exclusive low endpoint through
`ByteArrayUtil.strinc`, which advances past every key having those
bytes as a prefix — so resuming after `(bank_id, account_id)` skips
that account's remaining balance rows rather than the single record the
page stopped on. Endpoints are prefix-granular, not record-granular.

**So `scan` gains a composite cursor.** It should extract the whole
remaining primary key, `(subvec pk prefix-size)`, rather than the one
element at that position, and build the bound from all of them. The low
bound is then the last record's full primary key, and `strinc` over a
full key advances past exactly that record and its split parts.

Preserve the external shape while doing it. Cursors surface as API page
tokens, so a remaining key of one element should keep returning that
scalar rather than a one-element vector — then every current caller and
every issued `page[after]` token is unaffected, and only stores with a
composite tail see a composite cursor.

The alternative is to page on complete account groups and resume from
the last whole one. That works, but only while an account's balance
rows fit inside a page, and that is a limit chosen by guess. The
primitive should cursor properly instead.

The invariant this restores is currently undocumented and holds by
accident. Every store paged with a cursor today — policies and bindings
on single-element keys, parties and cash accounts on `[bank_id, X]` —
happens to cursor on the final key element. `transaction-legs` is
already shaped wrong at `[account_id, transaction_id, leg_id]`, and
escapes only because `get-transactions` never pages it.

## The merge belongs in `fdb`, not in interest

Pairing two stores on a shared leading key is a storage concern, so it
is `fdb/merge-scan` rather than a private fn in `interest`. Queenswood
has owned `components/fdb` since the FDB move, so this is one brick's
change.

Three things have to land together, and the second constrains the API
more than it first appears.

**Scan results carry the primary key.** `scan` returns opaque bytes
today and discards the key except for the cursor. A generic merge
cannot pull a join key out of bytes without knowing the schema, and it
must not know. Returning the key tail per record keeps `fdb` schema-
ignorant and makes merging entirely tuple-level. The public
`{:records …}` shape stays as it is, so no caller changes.

**It takes the db and store names, not open stores.** A merge outlives
any one transaction — five seconds caps them — so each page refill
opens its own. That also rules out returning a lazy sequence, since
realising one after its transaction closed is a latent bug. A reduce
keeps cursor lifetime inside `fdb`:

```clojure
(fdb/merge-scan config
                {:left  {:store "cash-accounts" :prefix [bank-id]
                         :limit 1000}
                 :right {:store "balances" :prefix [bank-id]
                         :limit 5000}}
                (fn [acc {:keys [key left right]}] ...)
                init)
```

`key` is the first key element after the prefix, `left` and `right` are
that key's records as bytes. Either side may be empty, which is how the
caller sees an account with no balances or a balance whose account has
gone. A group spanning a page boundary is refilled and delivered whole.
The reducing fn may return `reduced` to stop early, and an anomaly from
it ends the scan and propagates.

**It is explicitly not a consistent snapshot.** The two sides refill in
separate transactions, so a record written mid-scan can appear on one
side and not the other. That is inherent rather than incidental: `scan`
takes `:after` and `:before` as alternatives rather than composable
bounds — passing both silently drops one — so "the right store's
records over the left's key range" is not expressible, and the five-
second cap would defeat it anyway. Better stated as a property than
papered over.

## The records

**`InterestRun`.** The states become `RUNNING` and `CLOSED`.
`DISPATCHING` and `DISPATCHED` describe a fan-out that is not
happening. The count index and `check-daily-count` are unchanged.

**`InterestAccountRun`.** Keeps its state and gains two fields: the
`amount` accrued, and the balance it computed on. Both optional at the
wire level, per the proto2 rule below.

Recording the input balance is what answers "what was this account's
balance when the run touched it" without a separate snapshot store.
There is no `BalanceSnapshot` record type in this design and no second
sweep to maintain — the row that already exists carries the frozen
input.

It also settles what a defined cut-off time can mean here. A pass that
runs in seconds reads its first and last account seconds apart, so "as
of the close of business" is true of every account to within the run's
duration. A pass that took hours could not have claimed that, and the
fan-out version would have smeared it further.

Add a SUM index on `amount` grouped by `[bank_id, business_day, kind]`.
`fdb/record.clj` already has `sum-records`, and
`OutboundPayment_sum_amount_by_bank_business_day` is the precedent for
declaring one.

## The ledger entry

At the end of the pass the run posts one transaction for the whole
bank: debit 5100 interest expense, credit the 2400 interest-payable
control, for the total. The total comes from the SUM index, so it is
one read rather than a replay.

The invariant moves with it. Today each transaction balances on its
own. Here the double entry is not complete until the aggregate entry is
written, so *the run* is the thing that has to balance, and the check
is that the sum over rows equals the credit applied to customer
balances. The rows are what make that checkable, and they inherit the
redelivery property the previous design relied on for counts:
re-writing a keyed row does not move a sum any more than it moves a
count.

Two consequences to accept rather than discover. The trial balance is
out by the in-flight run until it closes — ordinary batch semantics,
but it should be written down. And per-account GL attribution is gone:
"which ledger entry corresponds to this account's accrual" is
answerable only through the row, not through the legs.

## Idempotency and retention

Removing the accrual transaction removes its idempotency key, so the
row is now the only per-account guard against double-crediting. It is
written in the same chunk transaction as the balance update, so a
re-run skips accounts already marked done and a half-finished chunk
rolls back whole.

Once the run is `CLOSED`, `check-daily-count` refuses a second pass for
that bank and day, so the rows stop being the guard. That is what makes
retention answerable, which the previous version could not do: the rows
are load-bearing while a run is open and become audit history after it
closes, so they can age out on a short retention without weakening
anything.

## What this needs

- `Balance` gains `bank_id`, and its primary key becomes `[bank_id,
  account_id, balance_type, currency, balance_status]`. A primary-key
  change is a rebuild, not a migration in place — and it comes last,
  for the reason set out in the order below.
- `fdb/scan.clj` cursors on the whole remaining primary key rather than
  one element of it, keeping a single-element tail as a scalar so
  existing page tokens are unaffected, and exposes the key alongside
  each record. `fdb/merge-scan` pairs two stores on a shared leading
  key. The fdb brick has no scan test today, so this brings one, along
  with a pair of test record types whose keys share a prefix.
- Separately, and not part of this work: the call sites that avoid
  cursors with a fixed ceiling and truncate in silence.
  `get-transactions` stops at 1000 legs and `get-balances` at 100 —
  both API reads that should expose a cursor — while `list-all-jobs`
  and the two policy-binding scans take 10000 and filter in memory,
  which should loop until exhausted instead.
- `InterestAccountRun` gains `amount` and the input balance, plus a SUM
  index on `amount`.
- `InterestRun`'s state enum loses `DISPATCHING` and `DISPATCHED` and
  gains `RUNNING`.
- `credit_carry` moves from the default posted row to the
  interest-accrued row, so the accrual pass reads and writes one row
  and never the one payments contend on.
- A balance bucket the pass expects and does not find is reported
  rather than papered over. Reading an absent row as zero was
  introduced when the per-account balance read was dropped, on the
  mistaken premise that an account which has never accrued has no
  accrued row. It has one from the moment it opens, so the zero
  reading masks exactly the corruption worth catching — but the
  account is logged and recorded done at zero, not failed.
- `accrual-leg` stops carrying `product-type` for the case where the
  posting has to open the bucket. Nothing opens a bucket on that path
  any more.
- `interest/core.clj` is rewritten around the pass: no
  `add-control-legs` and no `record-transaction` on the accrual path,
  a single aggregate posting at the end, and the accrued row written
  directly from the scan's frozen inputs rather than through
  `apply-legs`.
- Capitalisation drops its control legs and aggregates its ledger side
  the same way, keeping its per-account transaction. Its aggregate is
  not shaped like accrual's: both of accrual's GL legs are fixed, so a
  total per currency suffices, while capitalisation credits whichever
  deposit control the product type rolls into. So
  `InterestAccountRun` gains `product_type` and a second SUM index
  grouped by it, and the run posts one entry per (currency, product
  type) — each balancing on its own — rather than one per currency.

## The order, and why the rebuild is last

The rebuild does not gate the pass, which is worth stating because the
opposite looks true. `Balance` is keyed from `account_id`, account ids
are globally unique, and `CashAccount` scanned under `[bank_id]` is
ordered by `account_id` too — so both streams are already in the same
order and the merge works against today's key. Other banks' balances
arrive as right-only groups, which interest skips. That is wasteful on
a multi-bank deployment and exactly right on a single-bank one, and it
means the pass can be built and proved before anyone rebuilds the
record every other brick reads.

1. **The records.** `InterestAccountRun` gains the amount and the
   inputs it was computed from, and the SUM index. `InterestRun`'s
   states become RUNNING and CLOSED. No change to what the ledger
   does.
2. **Silent accrual.** Drop the per-account transaction and its
   control legs; write the balance and carry, and record the amount on
   the row. The behaviour change, and where the test churn lands.
3. **The aggregate ledger entry.** One posting per run off the SUM
   index. Between 2 and 3 the books do not balance, so they want
   landing together even if written apart.
4. **The merged scan.** Swap the paged account scan and the
   per-account balance read for `fdb/merge-scan`, so each account
   arrives with its balances attached.
5. **The frozen write.** Move `credit_carry` to the interest-accrued
   row and write that row directly from the scan's frozen inputs
   instead of through `apply-legs`, then chunk the writes and preload
   the rate map. This is what step 4 was for: pairing the scans buys
   nothing while the write path re-reads what the scan already
   delivered.
6. **Capitalisation.** The same treatment for its ledger side, keeping
   its per-account transaction — and one entry per (currency, product
   type), because its credit side varies where accrual's does not.
7. **The `Balance` primary key.** Now only about not scanning other
   banks' rows.

Outside the order and blocking none of it: whether a reporting cut
that has to foot is separately required. That question is taken up in
[statementing.md](statementing.md), which answers it from the
transactions and so brings back no snapshot artefact.

## What this supersedes

- The per-account command fan-out, and the `interest` command topic
  keyed per account that went with it.
- `dispatch_cursor` on `InterestRun`, which existed to resume a
  fan-out.
- The claim in [account-serialisation.md](account-serialisation.md)
  that interest joins per-account command ordering. It does not, and it
  no longer needs to: the accrual path stops writing through
  `apply-legs` on the contended rows, which is what that ordering was
  meant to protect.
- The scheduler caveat. `scheduler/core.clj` calls interest
  synchronously and uses the return value, and with a pass this short
  that call stays synchronous and keeps meaning "the work finished".

## Scope and caveats

- **Capitalisation still has the volume.** It runs monthly or yearly
  rather than daily, but on the day it runs it writes a transaction per
  account. Dropping its control legs removes the hot rows, not the
  cardinality.
- **The rebuild of `Balance` was the risky step**, and it was a rebuild
  of the record every other brick reads. It landed on its own. The cost
  was not the key change but the reach: `bank_id` heads the key, and
  neither `Transaction` nor `TransactionLeg` carries a bank, while
  `CashAccount` is keyed `[bank_id, account_id]` so a bank cannot be
  derived from an account id either. So every posting and read path had
  to take a bank explicitly — `apply-legs`, `new-balances`,
  `record-and-post`, and the four balance reads — rather than the key
  simply gaining a column.
- **Provisioning a record store per bank was the alternative**, and is
  now closed off: `bank_id` on the record was chosen instead, and
  taking the per-bank route later would mean rebuilding `Balance` a
  second time. The reasoning is kept because it applies to any record
  that follows. A per-bank store
  gives the pass a contiguous per-bank scan with today's key, drops
  `bank_id` from records, and turns tenant isolation from a predicate
  every caller must remember into a store they would have to open by
  mistake — `keyspace.clj`'s `scoped` already qualifies store names for
  the deployment-level prefix, so the mechanism is half there. It is not
  a clean sweep, though: platform-scope records such as policy stay
  global, so it means maintaining two store scopes, and index builds
  then fan out per bank on every schema change. If it is ever taken up,
  it should be decided on isolation and bank offboarding rather than on
  scan shape.
- **The retention number is still unpicked**, though no longer hard.
  Nobody has chosen how many days of closed-run rows to keep, but
  [statementing.md](statementing.md) settles that nothing outside a run
  needs them — the movements a statement or a tax certificate reports
  come off the transactions — so the window can be short and is not a
  reporting decision.
- **The carry is not recognised in the ledger, deliberately.** A
  remainder averages half a minor unit per account, so a million
  accounts hold roughly £5,000 the books do not show. The books still
  balance — the customer accrued buckets and the 2400 control both
  carry whole units only — so this is an unrecognised obligation
  rather than a break, and at that size it is a rounding error. It is
  also not a flow: a carry replaces its predecessor rather than
  accumulating, so recognising it would mean a true-up on the change
  in aggregate carry, netted against the accrual in the same run to
  avoid counting a rolled-over unit twice. Not worth the netting bugs
  unless a reporting cut that has to foot is required, in which case
  it belongs with that.
- **The pass is single-writer per bank.** Two concurrent passes for one
  bank would both read the same balances; `check-daily-count` makes
  that a rejection rather than a race, but it is a limit rather than a
  guarantee.
- **Progress polling still reads at SNAPSHOT.** A count index read at
  SERIALIZABLE joins the read-conflict set, so polling would conflict
  with the pass it is observing.

## Rules that cost real time to learn

- **A proto2 `required` field holding its default value fails to
  parse.** protojure drops zero, false, and empty defaults on the wire,
  and the Java FDB parse then rejects the record as missing a required
  field. `amount` must be `optional` — a zero accrual is the common
  case for a low balance.
- **Renaming or adding a proto enum label needs a forced prep.** Run
  `clj -X:deps prep :aliases '[:dev]'` with `:force true` after
  changing `InterestRunState`, or full-system startup fails serialising
  against the stale generated class while brick tests still pass.
- **`0` is truthy in Clojure, and this code already depends on it.**
  `daily-interest` can return `whole-units 0` with a carry, which is a
  real result and not an absence. The pass has to write the carry in
  that case and record the row, not skip the account.
- **Interest accrues on available, and available is a computation.**
  It spans the posted bucket and the pending-outgoing reservation
  against it, and excludes pending-incoming — so it cannot be read off
  a single row, and a pass holding only the posted bucket will quietly
  pay interest on money the customer has already committed.
- **A balance bucket the product declares is an invariant, not a case
  to handle.** `balance-products` on the product version decides which
  buckets an account opens with, and the three customer product types
  the pass admits all declare an interest-accrued posted bucket. Code
  that reads an absent bucket as zero, or opens one on the posting
  path, is covering for a corruption rather than a real state. Saying
  so and carrying on is the right response; failing the account is
  not, since the account is not what is wrong.
- **Which row a batch writes decides whether it can write unread.** A
  value frozen in an earlier transaction is safe to write back only
  where nothing else writes that row. The accrued bucket has two
  writers, both in `interest`, so it qualifies; the default bucket has
  every payment, so it does not. Read the writers before deciding, not
  the readers — this is why the carry has to move rows before the
  write path can be simplified.
- **An exclusive scan endpoint skips a whole prefix, not one record.**
  `TupleRange.toRange` puts it through `ByteArrayUtil.strinc`. Any store
  paged on a cursor element that is not unique — balances under a bank,
  legs under an account — must page on complete groups, or it drops
  records at every page boundary and reports no error.
