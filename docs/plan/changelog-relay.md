# Plan: internal reactive flows via the changelog relay

## Context

ADR-0008 chose in-process changelog watchers for reactive state
transitions, and named the alternative in its "Future" section: give the
watcher the narrow job of relaying to the message bus, and move consumers
to the broker. That path is now built. This plan tracks what remains.

The starting premise was that FDB watches are unreliable. They are not
used — nothing calls `.watch()` anywhere. Every "watcher" is a polling
changelog consumer: `components/fdb/watcher.clj` spawns a daemon thread
calling `changelog/process` every 100 ms. A sentinel key is bumped on
every commit and documented as "suitable for FDB watches", but nothing
reads it. The real problems are different:

- **Domain work runs inside the changelog checkpoint transaction.**
  `changelog/process` invokes `(handler ctx changelog-bytes)` inside
  `record-db.run`. `idv/watcher.clj` does a Kafka `message-bus/send` *and*
  opens its own nested FDB transactions from in there. FDB caps a
  transaction at 5 s and re-runs it on retry.
- **Errors are swallowed.** `fdb/watcher.clj`'s loop is
  `(catch Exception _)` with no logging. A permanently failing handler
  spins at 10 Hz forever, with no DLQ.
- **Dedup drops transitions.** `changelog/process` defaults to
  latest-entry-per-record-id, and the record-id is the entity id, so two
  transitions inside one poll window collapse and the earlier is lost.
- **One cursor per `consumer-id` pins its whole service to `replicas: 1`.**

## Done

- **#268** — generic `changelog-relay` runner (passes
  `{:deduplicate? false}`, logs failures) and a `relay-service` that owns
  every cursor at `replicas: 1`. Both adapter relays moved into it, so the
  adapter services no longer own a cursor.
- **#269** — the shared `ChangelogEvent` envelope, `cash-accounts`
  migrated to relay + event-processor, and `changelog-relay/event-consumer`
  (see "Upstream" below for why it exists).
- **Phase 3** — store `parties`, consumer `idv`. The flow that carried
  the real defect: `idv/watcher.clj` did a Kafka `message-bus/send` *and*
  opened nested FDB transactions from inside the changelog checkpoint
  transaction. Both are gone. `party/changelog.clj` writes the envelope
  carrying a new `party-status-changed` Avro event on a `parties-event`
  channel; `idv/core.clj`'s `initiate-for-party` holds the watcher's
  `get-idv-by-party` guard verbatim, and runs outside any changelog
  transaction. The parties cursor keeps `consumer-id: idv-party-watcher`.

  No cutover was needed here either, but for the opposite reason to
  Phase 5: `PartyChangelog` put enum fields at 2 and 3 where
  `ChangelogEvent` has length-delimited strings, so an old entry cannot
  masquerade as an envelope. The handler skips-and-logs rather than
  throwing, so such an entry cannot wedge the cursor behind it.

- **Phase 4** — store `idvs`, consumer `party`. The mechanical twin,
  and the last watcher. `party/watcher.clj` is gone: its
  `idv-status->party-transition` map and its
  `(= :party-status-pending (:status party))` gate are now
  `party/core.clj`'s `apply-idv-status`, driven by a
  `party/events.clj` Processor on an `idvs-event` channel.
  `idv/changelog.clj` writes the envelope carrying a new
  `idv-status-changed` Avro event; its `causation-id` is the
  *party* id, not the verification id, because the party is what the
  consumer advances and so what the future partition key has to keep
  in sequence. The idvs cursor keeps `consumer-id: parties-watcher`.

  Same cutover story as Phase 3, and for the same reason:
  `IdvChangelog` put enum fields at 2 and 3 where `ChangelogEvent`
  has length-delimited strings, so an old entry cannot masquerade as
  an envelope.

  The retired changelog protos went with it — `CashAccountChangelog`,
  `PartyChangelog`, `IdvChangelog` and their `schema/interface.clj`
  exports all had no writer or reader left. `BankChangelog` stays:
  `bank/store.clj` still writes it, and no relay reads it yet.

## Remaining

The migration unit is one **store**, not one brick: a store's changelog
format change must land atomically with whichever brick consumes it.

### Phase 5 — converge the adapters — done

Both bespoke `relay-handler`s are retired; `clearbank-adapter-relay` and
`onfido-adapter-relay` name `changelog-relay/envelope-handler`. No
cutover was needed, as predicted: the outbox protos reuse
`ChangelogEvent`'s field numbers and wire types, so an entry already on
a cursor decodes unchanged. Neither handler read `outbox_id`, which is
the only field that differs in name.

That alignment used to be a comment in the proto. It is now a test —
both relay interface tests take the shared handler from their rig and
relay a stored outbox entry to the bus, so the claim fails loudly if
the shapes ever drift apart.

Revisiting replicas is still open.

### Kafka test isolation

FDB is scoped per rig now, via `fdb/keyspace-prefix` — the test rigs
generate one per boot, so they no longer share stores, changelog or
cursors. Kafka is not: rigs share a broker, the topic names, and fixed
`group.id`s, so one rig consumes another's events. The same lever
applies — a per-rig topic prefix — but it reaches every entry in
`kafka-topics.yml` and `kafka-all-test.yml`, and the kafka component is
still mono's. Until then, assertions over span or message counts must
name the event they mean rather than aggregate.

### Docs and lint

Deferred wholesale and now the largest debt: 94 "watcher" mentions across
`docs/tdd/{parties,cash-accounts}.md`,
`docs/tdd/processor-bricks.md`, `docs/recipes/lifecycle-transitions.md`,
`docs/recipes/system-components.md`, `docs/recipes/deployment.md` and
ADR-0019. `parties.md` is worst at 25, including two mermaid diagrams.
ADR-0008 and ADR-0019 are already updated; only references to deleted files
were fixed elsewhere.

`.claude/skills/check-processors/checks.sh` and
`.claude/skills/check-docs/checks.sh` still encode `watcher.clj`. No
`watcher.clj` remains, so `check-processors`' `watcher-fdb-scope` check
now passes vacuously — worse than failing. This is the next thing to
do, and it can be written once, in full.

## Rules that cost real time to learn

- **Never mint a fresh `consumer-id`.** `changelog/scan` range-reads
  everything after the checkpoint and calls `.asList` with no limit, inside
  one FDB transaction. A new id starts at `nil` and would try to read a
  store's entire changelog history in a single transaction, and re-publish
  every historical transition. Reuse `parties-watcher` and
  `idv-party-watcher` verbatim (also ADR-0019's cursor-continuity rule).
- **A `*-test.yml` referencing a component-kind needs that brick in the
  rig's require bundle** — and a green `project:dev :all` does *not* prove
  it, because another brick's test namespace may load the interface first
  and register the kind globally. Only the brick-level run
  (`test brick:test-scenarios project:dev`) catches it. The reverse also
  holds: deleting a bare require de-registers kinds.
- **Envelope proto fields stay `optional`.** protojure omits zero and empty
  values on the wire, and a proto2 `required` scalar holding its default
  then fails to parse.
- **The changelog is versionstamped; the sentinel is not what orders it.**
  `changelog/write` uses `SET_VERSIONSTAMPED_KEY`, keying every entry by
  `(commit-version, user-version)` — `.claimLocalVersion` keeps two writes
  in one transaction distinct. Global commit order is therefore already in
  every key, which is *why* `scan` is a range-read from the checkpoint. The
  sentinel is a separate conflict-free `ADD` counter whose only unique
  property is "learn something changed without scanning" — the wakeup an
  FDB watch would use. Nothing reads it. Ordering survives FDB and is then
  discarded at the relay, by the unkeyed `message-bus/send` in item 1
  below.
- **Test rigs share state at two layers, and FDB is the lesser one.**
  FDB is now scoped by `fdb/keyspace-prefix`, which the test rigs generate
  per boot. Kafka is not: rigs share a broker, the topic names in
  `kafka-topics.yml`, and fixed `group.id`s, so one rig's published events
  reach another rig's consumers. Measured on the parties relay — the
  api-scenarios rig sees 75 joined / 21 unjoined `party-status-changed`
  spans alone, and 78 / 193 when the scenarios rig runs too.
  A ratio assertion over all `process-event` spans therefore measures
  another rig's traffic; scope it to an event only one rig publishes.
- **`meta-store`'s `path:` is not an isolation lever.** It scopes the
  `FDBMetaDataStore` only. Records hang off `store-name`, and changelog /
  sentinel / checkpoint off the `"mono"` root — which is what
  `fdb/keyspace-prefix` now qualifies.
- **A relay must be handed the same prefix its writer used.** The writer
  recovers it from the record-store; a runner only gets a `record-db`, so
  it cannot discover one. A mismatch reads an empty changelog rather than
  erroring.
- Run `clj -X:deps prep :aliases '[:dev]' :force true` after any proto
  change.

## Upstream, in mono

Both gate real scale-out, in this order:

1. **`message-bus/send` needs a partition key**, and topic partition counts
   need raising — in that order. Every topic is `partitions: 1` today and
   `KafkaProducer.send` passes no key, so extra replicas are idle standbys;
   raising partitions without a key silently breaks per-entity ordering.

   Keying does not restore commit order across the topic; it makes global
   order unnecessary. Kafka orders within a partition only, so hashing an
   entity's id to a partition keeps that entity's own transitions in
   sequence, and nothing depends on one entity's order relative to
   another's. The envelope already carries the value: `causation_id` is the
   `party-id` / `account-id` for the events written so far.

   Unkeyed reordering is invisible rather than loud. A `closing` event
   overtaking its `opening` lands on `complete-status-transition`, whose
   source-status guard skips silently — by design, so redelivery is a
   no-op. The guard that makes replay safe is the guard that hides
   reordering.

   Per-partition order also depends on producer idempotence, which is on
   today but only by default. Verified against the pinned kafka-clients
   4.3.1: with `acks: all` (all 75 producer declarations) the effective
   config is `enable.idempotence=true`, `retries=2147483647`,
   `max.in.flight=5`. Set `acks: 1` on any producer and idempotence
   silently becomes `false` — the client disables it rather than raising
   when a conflicting config appears and idempotence was not explicitly
   asked for — leaving retries and in-flight at the values that reorder
   within a partition. Setting `enable.idempotence: true` explicitly on
   the event producers converts that into a boot-time `ConfigException`.
2. **`event/process` should rethrow on anomaly.** It wraps the handler in
   `error/try-nom`, logs, and returns — so the Kafka consumer acks and the
   event is lost, where the watcher it replaces redrove forever. That is why
   `changelog-relay/event-consumer` subscribes directly and rethrows;
   fixing it upstream retires that component.

   Every event consumer now uses `event-consumer`, so nothing instantiates
   `event-processor/event-processor` any more. The last two on it were
   `payment` (`schemes-payments-event`) and `idv` (`idv-event`) — which
   were exactly the two carrying webhook-derived facts, the ones that
   cannot be re-derived if dropped.

Both of those live in mono's `kafka` / `event` components. **FDB does not.**
PR #270 moved it into this workspace, so everything below is a local
change in `components/fdb` — check `git log -- components/<name>` before
assuming any component is upstream.

## Local, in components/fdb

1. `changelog/process` needs a `:limit` batch cap. The handler runs inside
   the checkpoint transaction, so with `{:deduplicate? false}` a burst
   issues N publishes inside one FDB transaction.
2. `read-checkpoint` should read through the processing transaction. It
   opens its own, so the checkpoint key never enters the outer
   transaction's read-conflict set and two relays would not conflict — FDB's
   own optimistic concurrency would otherwise make multi-replica relays safe
   without a lease.
3. Retire `fdb/watcher` and `fdb/watchers`. Phase 4 removed the last
   `fdb/watchers` declaration, so both are now dead code — and dead code
   whose loop swallows every exception with no logging (see Context),
   which is the trap if anything ever wires it again. Upstreaming
   `components/changelog-relay` to mono is a separate question, and only
   sensible after that.

## Deliberately not built

**Leader election.** A cursor has exactly one owner, but the relay's work
is decode-and-publish — O(1) per entry, no business logic. `replicas: 1`
plus a durable cursor already survives a crash; leader election would only
shorten the gap before someone resumes. It buys failover, not throughput.
The relay tier scales by sharding stores across deployments, not by adding
replicas.

## Verification

- `clojure -M:poly test project:dev :all`
- `clojure -M:poly test brick:test-scenarios project:dev` and
  `brick:test-api-scenarios` — **in isolation**, per the rule above
- boot `monolith-service`; unregistered component-kinds surface at start
- scale `relay-service` to 0, confirm events stop; back to 1, confirm they
  resume from the checkpoint with nothing lost
