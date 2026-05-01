# 2. FoundationDB Record Layer for persistence

## Status

Accepted.

## Context

A banking system has hard requirements on persistence:

- Multi-record atomicity. A transfer touches both account balances,
  both posting legs, and a transaction record. They commit together or
  not at all.
- Strict serializability. The wrong consistency model produces
  money-losing bugs.
- Indexed access and ordered scans across each entity type.
- A reactive primitive — something downstream of writes that
  state-machine transitions can hang off (IDV → party activation,
  account close → balance freeze).

[FoundationDB](https://www.foundationdb.org/) provides all of this. Its
[Record Layer](https://foundationdb.github.io/fdb-record-layer/) sits on
top of FDB's ordered KV store and provides indexed, schema'd record
stores with multi-store transactions. Apple uses FDB internally;
Snowflake's metadata layer is built on it. Strict serializability is
the default mode.

The shortlist we considered:

- **Postgres.** Well understood, every cloud provider has a managed
  offering, huge ecosystem. But multi-record atomicity at scale runs
  into sharding, and the operational story there is non-trivial.
  Reactive primitives have to be bolted on (LISTEN/NOTIFY, logical
  replication, external CDC pipelines).
- **CockroachDB / Spanner / Yugabyte.** Distributed SQL with
  multi-record transactions. Either ties us to a vendor (Spanner) or
  to a smaller ecosystem (Cockroach, Yugabyte).
- **DynamoDB / Cassandra-style stores.** Scale, but no multi-record
  ACID in the strict sense, which is disqualifying for a bank.

## Decision

We will use FoundationDB with the Record Layer for all persistence.
Each entity type lives in its own record store; operations spanning
multiple stores happen inside a single FDB transaction. We accept the
operational tax of running FDB ourselves — there is no managed
offering — in exchange for the consistency guarantees and the
architectural primitives the Record Layer makes available.

## Consequences

Easier:

- Multi-store ACID transactions everywhere, by default. Double-entry
  posting and the atomicity it requires are free.
- Strict serializability without configuration. The strongest
  consistency model in the literature, taken as a baseline rather than
  a knob to tune.
- **The changelog is the transactional outbox.** A committed write
  appears on the per-store changelog cursor in commit order, as part
  of the same atomic write. No separate outbox table, no publisher
  process polling it, no race between "wrote the state" and "published
  the event" — they are the same write. In other databases the
  transactional outbox pattern needs deliberate per-service plumbing;
  here it falls out of the storage engine. How we *consume* this — in-
  process watchers vs message-bus publishing — is a separate decision
  (covered in a later ADR).
- **Versionstamps replace wall-clock time as the ordering primitive.**
  Every committed write receives a monotonically-increasing,
  cluster-assigned versionstamp. Keying records and events by
  versionstamp instead of `now()` sidesteps clock skew, NTP drift, and
  the "two events in the same millisecond" ambiguity that plagues
  systems relying on wall time for ordering. Queenswood does not yet
  exploit this everywhere, but the option is available wherever
  ordering matters.

Harder:

- **No managed offering.** AWS, GCP, and Azure do not ship
  FoundationDB; no credible third-party managed FDB exists. Whoever
  runs Queenswood in production carries the full operational burden:
  cluster setup, version upgrades, backup and restore, monitoring,
  capacity planning, disaster recovery. This is the single biggest
  cost of the decision and we are not going to downplay it.
- Smaller community than Postgres or Mongo. Less Stack Overflow, fewer
  prebuilt observability integrations.
- The Record Layer is a Java library on top of FDB — another layer to
  learn, debug, and reason about. Schema evolution flows through
  Record Layer metadata versions, which the `migrator` brick manages.
- Query patterns are limited to what indexes and ordered scans can
  deliver. No ad-hoc SQL.

If a managed FoundationDB offering appears from a major cloud provider
or a credible third party, the operational-tax half of this trade-off
largely goes away and the decision becomes pure upside. Worth
re-evaluating periodically.
