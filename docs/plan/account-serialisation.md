# Plan: serialise account activity, soften bank-level limits

## Context

Payment limits are enforced at two scopes, and they want opposite
treatment.

**At account level the limit must be hard.** `balance/domain.clj`'s
`check-available` is a policy limit on the computed `available`
balance, evaluated per account by `apply-legs`' `(group-by :account-id
legs)`, inside the payment's FDB transaction. An account cannot spend
what it does not have, and that is not negotiable.

**At bank level the limit is a safety net.** `check-daily-count` and
the `:time-window-daily` `check-amount` both key on
`#{:bank-id :business-day :amount}`. They exist to catch a runaway, not
to be exact to the penny.

Today both are enforced the same way, and the mechanism is FDB's
optimistic concurrency. The whole payment — read the debtor account,
read the aggregates, evaluate policy, record the transaction, apply the
legs, save — is one `store/transact`. Two concurrent payments from one
account both read that account's balances and both write them, so the
second to commit conflicts and retries against fresh state. Correct,
and paid for in retries.

That is fine at one processor replica. It stops being fine when the
processor tier scales, and the reason is the bank-scoped aggregate:

```clojure
(.evaluateAggregateFunction ... IsolationLevel/SERIALIZABLE)
```

`fdb/count-records` reads the COUNT index at SERIALIZABLE, which puts
the aggregate key in the read-conflict set. The index is maintained by
atomic mutations as payments commit. So *every* concurrent payment in a
bank conflicts with *every other*, regardless of account. Payments
within a bank already serialise on the counter, and no amount of
partitioning changes that while the read stays serialisable.

## Decision

Serialise account activity by routing, and soften the bank-level check
to match what it is for.

1. **Key payment commands on `debtor-account-id`.** Payment commands
   for one account land on one partition and are processed in order, so
   payments stop contending on that account's balances. The
   available-balance limit stays exact and stops costing retries
   between payments — other balance writers still reach it
   concurrently, and FDB still resolves that (see Scope and caveats). This
   is also the right sharding axis: one account is one serial stream,
   N accounts go N ways parallel.
2. **Read the bank-level aggregate at SNAPSHOT.** It does not join the
   read-conflict set, so payments across different accounts stop
   colliding on it. The count can be stale by the number of in-flight
   concurrent payments, which means the daily limit can be exceeded by
   that much under load — acceptable for a safety net, and the reason
   this is the *soft* half of the pair.

These are one change, not two. Keying without the SNAPSHOT read buys
nothing: the serialisable aggregate re-serialises the whole bank.

## Keys are declared, never inferred

A partition key is a routing decision. `causation-id` is lineage. They
must not be the same field, and mono must not derive one from the
other.

If `event/publish` keys on `(:causation-id envelope)` automatically,
then a publisher that leaves `causation-id` nil silently gets an
unkeyed topic with no ordering and no signal, and a publisher that sets
it for tracing silently changes partitioning. Both directions fail
quietly, which is the same class of fault as unkeyed reordering itself:
invisible rather than loud.

So mono should *support* `{:key …}` in the send opts and apply it only
when asked. Queenswood passes it deliberately, at each publish site,
where the choice is visible in review and greppable afterwards. The
cost is that every site that wants ordering has to say so. That is the
point.

This corrects the guidance in the original mono handoff, which proposed
deriving the key inside `event/publish`.

## The key travels in the envelope, not in `causation-id`

A command keyed on an account must produce events keyed on the same
account, or ordering is preserved on the way in and discarded on the way
out. The relay is generic and must stay that way, so the key has to
travel *in* the envelope rather than be inferred by the relay.

It must not be `causation-id`. That is the same conflation rejected
above, and this codebase already demonstrates why. Three writers set
`causation-id` to their own entity, but `idv/changelog.clj` sets it to
the *party* id rather than the verification id — chosen because the
consumer advances the party, which is a routing decision wearing a
lineage field's clothes. It works only because entity and ordering unit
happen to coincide.

Where they don't coincide it fails outright: the ClearBank outbox sets
`:causation-id (str (utility/uuidv7))`. Keying on that is worse than
not keying, because it looks keyed and scatters perfectly.

So `ChangelogEvent` gains an explicit optional key field. The writer
declares it, the relay reads it and passes `{:key …}` to the bus, and a
writer that sets nothing gets an unkeyed publish — the same
declared-never-inferred rule the commands follow.

Note that nothing downstream of a payment needs this yet: no
`write-changelog` exists in `payment`, `transaction` or `balance`, so a
keyed payment command currently produces no events to carry the key
onward. This matters when it does — and it will, if payments ever grow
a status-changed event of their own.

## What this needs

**In Queenswood:**

- Payment routes set the key for the commands they dispatch —
  `debtor-account-id` for internal and outbound submits. The route has
  it; the envelope does not carry it today.
- `changelog-relay`'s envelope handler passes a key explicitly when
  republishing, rather than relying on a mono default.
- An isolation option on `fdb/count-records`, defaulting to today's
  SERIALIZABLE so other callers are unaffected, with the payment limit
  checks opting into SNAPSHOT.

**In mono:**

- `{:key …}` honoured from send opts, never derived.

## Scope and caveats

- **Payments are not the only writer, so serialisation is partial.**
  `transaction/record-transaction` and `interest`'s accrual and
  capitalisation commands also post legs to account balances. Keying
  payment commands removes contention *between payments* on one
  account; a payment concurrent with a `record-transaction` on the same
  account still lands on an unrelated partition and relies on FDB
  conflict detection, exactly as today. That is safe — the conflict is
  the backstop and `check-available` cannot be breached — but the
  guarantee is "payments on an account are serialised", not "account
  activity is serialised".

  `interest` is structurally unkeyable: accrual is a batch across many
  accounts, so there is no single account to key it on. Any future
  attempt to make account serialisation total has to solve that first,
  and the answer is probably to split the batch per account rather than
  to key it.
- **Debtor-side only.** Keying on `debtor-account-id` serialises
  debits. An internal payment `A→B` and another `C→B` land on different
  partitions and both write B's balances, so credits still rely on FDB
  conflict detection. That is acceptable: `check-available` runs for
  every account in the legs, but on a credit the post-value exceeds the
  pre-value, so it cannot reject. A creditor-side conflict costs a
  retry and never a wrong answer — the key is where the check can
  actually fail.
- **Inbound payments are not keyed.** They arrive from a ClearBank
  webhook, not a command, so they credit an account without passing
  through this path. Same reasoning applies: a credit cannot breach the
  available-balance floor.
- **Account-scoped velocity limits do not exist yet.** Every count and
  amount limit today is bank-scoped. Adding per-account daily count and
  amount becomes cheap *after* this change, because they would read
  uncontended — but they are not a prerequisite for it.
- **An unsupplied key is a null key.** Nothing is invented. A command
  dispatched without one is sticky-batched by Kafka or round-robined by
  Pulsar. So a topic can carry keyed and unkeyed commands at once, and
  per-account ordering holds only for the keyed ones.
- **Command topics stay at `partitions: 1` until this lands.** Unkeyed
  plus multi-partition is the combination that reorders. The event-side
  partition work will make raising counts feel safe; it is safe for
  event topics only.

## Rules that cost real time to learn

- **The aggregate index is not the problem; the isolation level is.**
  `count-records` uses `evaluateAggregateFunction` for an O(1) read of
  a maintained COUNT index. It does not scan payment records. Reading
  it at SERIALIZABLE is what makes every payment in a bank conflict.
- **`check-available` is a policy limit, not a hardcoded guard.** It
  goes through `policy/check-limit` on `:balance` with
  `{:computed {:name "available"}}`, so a bank or tier can configure
  it. Changing how it is enforced must not change that it is
  policy-driven.
- **Sticky is not random and not fixed.** An unkeyed Kafka producer
  batches to one partition until the batch closes, then switches — so a
  test asserting "one entity's records share a partition" passes
  unkeyed and proves nothing. Assert the partition the key selects.
  Pulsar round-robins per message instead, so the same test fails there
  for a different reason.
