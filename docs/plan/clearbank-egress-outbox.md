# Plan: ClearBank egress via changelog-as-outbox + relay

## Context

The ClearBank adapter is a stateless translator with two egress edges, both
fire-and-forget (verified end to end):

- **Webhook → event.** `TransactionSettled` / `Rejected` / `Held` webhooks are
  turned into bus events (`transaction-settled`, …) by `publisher/publish-*`
  and the handler returns **200 unconditionally** — a failed publish is only
  logged, so the event is permanently lost while ClearBank is told "success"
  (`webhook/handlers.clj:29-38` etc.). The `nonce` dedup is an in-memory atom,
  not durable.
- **Command → HTTP.** `submit-payment` is consumed and the ClearBank
  `/v3/payments/fps` POST is made inline; an HTTP failure surfaces as an
  anomaly (not a throw), so the Pulsar command is **acked anyway, no retry**
  (`clearbank.clj:53-57`). A crash between consume and POST loses the submit.

The fix is the transactional-outbox + relay pattern. Per ADR-0008 the codebase
already treats **FDB's changelog as the outbox**, drained by changelog
watchers; `fdb/changelog.clj` already implements the at-least-once drain
(versionstamped order, per-consumer checkpoint advanced in the same
transaction as processing). The relay is "a watcher whose single job is: read
cursor → publish to bus → advance cursor" (ADR-0008 Future section).

Decisions taken: **changelog-as-outbox** (reuse `fdb/process-changelog`), and
**ClearBank first** as the reference implementation, structured so Onfido and
the producer-side edges (`bank-idv` `submit-idv-check`, `bank-payment`
`submit-payment`) follow in later branches.

The adapter owns no FDB state today, so the core lift is **giving it a store**
whose changelog is the outbox.

## Design

The two edges have genuinely different shapes and are phased accordingly.

### Phase 1 — webhook → event (the reference, self-contained) — DONE

Implemented and verified: full dev matrix 140 tests / 1797 assertions green,
api-scenarios settlement path exercises webhook → outbox → relay →
bank-payment end to end. Notes below describe the shipped design.


The clean demonstration of changelog-as-outbox, and it fixes a real event-loss
bug. New component `bank-clearbank-relay`:

- **Record type `ClearbankOutboxEvent`** — proto under
  `schemas/clearbank/`, registered in `schema.proto` RecordTypeUnion +
  `fdb-record-types.yml` + `fdb-test.yml`. Fields: `outbox-id`, `event-name`
  (the bus event to emit), `payload` (the already-mapped event data, Avro
  bytes), `dedup-key`, `created-at`. A unique index on `dedup-key` gives
  webhook-retry dedup.
- **`store.clj`** — `save-event` writes the record and a changelog entry
  (`fdb/write-changelog`) in one transaction; a uniqueness violation on
  `dedup-key` maps to "already recorded" (idempotent webhook retry).
- **`relay.clj`** — the changelog handler: for each entry, deserialize the
  stored event and publish it to the bus verbatim via `events/publish`. It
  **throws on publish failure** so the checkpoint does not advance — the entry
  is redriven next tick and downstream (`bank-payment` event processor) dedupes
  on the event key. This is the at-least-once relay.
- **`system.clj`** — a `payment-processor`-style store config plus an
  `fdb/watchers` consumer (`consumer-id: clearbank-relay`,
  `store-name: clearbank-outbox`, handler = the relay).

Webhook handlers (`webhook/handlers.clj`) change from
map-then-publish-then-200 to **map-then-persist**: build the event data (reuse
the existing `publisher` mapping), `save-event` in one FDB transaction, return
**200 only on commit success, 5xx on failure** so ClearBank retries. The
`nonce` in-memory dedup is replaced by the durable `dedup-key`. The relay does
the actual bus publish. `inbound-cop-request-received` is a synchronous
query/response, not egress — left unchanged.

Reuse note: `fdb/process-changelog` runs the handler inside the checkpoint
transaction. For a bus publish that is acceptable (a txn retry republishes;
downstream dedupes) and matches ADR-0008. If republish noise matters later,
extract an out-of-transaction drain (read → publish → checkpoint-committed
entries) — which Phase 2 needs anyway.

### Phase 2 — command → outbound HTTP (distinct shape)

Consume `submit-payment` → one FDB transaction: idempotency-check → save a
`ClearbankOutboundIntent` (pending FPS call: url, body, `dedup-key =
endToEndId`) → ack Pulsar. A relay makes the ClearBank POST **outside any FDB
transaction** (FDB's 5s limit forbids a blocking HTTP call inside the checkpoint
txn, and a txn retry would re-issue the call), then marks the intent sent in a
separate transaction. ClearBank dedupes on `endToEndIdentification`; a rejection
persists a `ClearbankOutboxEvent` that the Phase-1 relay publishes as
`transaction-rejected`.

This needs an out-of-transaction drain (not `process-changelog`'s in-txn
handler) and depends on ClearBank-side dedup, so it lands after Phase 1 — same
branch or next, to be confirmed.

## Ingress (both phases)

Ingress is idempotent consume-then-ack, not an outbox: check/write the dedup
key in the same FDB transaction as the effect, ack Pulsar only after commit.
`bank-idempotency` store primitives already compose into an outer transaction;
Phase 2's unique `dedup-key` index gives write-side dedup directly.

## Reusability for Onfido

The `save-event` + changelog-relay + `publish` shape is adapter-agnostic. Onfido's
`check-completed` → `idv-completed` is the same Phase-1 pattern with its own
record type. Keep the relay handler and store helpers generic enough to lift.

## Critical files

- New: `components/bank-clearbank-relay/` (proto, store, relay, system,
  interface); `schemas/clearbank/*.proto`; record-type entries in
  `fdb-record-types.yml` + `fdb-test.yml`; `schema.proto` union slot.
- Edited: `bases/bank-clearbank-adapter/webhook/handlers.clj` (persist instead
  of publish), the adapter `system.clj` (wire store + relay + record-db/store
  into the webhook request), the adapter `deps.edn` / project deps.
- Test systems: the adapter store needs `record-db`/`record-store` in the
  monolith + scenario test configs that run the adapter.

## Verification

- New brick test for `bank-clearbank-relay`: `save-event` dedups a repeated
  `dedup-key`; the relay publishes the stored event; a publish failure leaves
  the checkpoint unadvanced (redriven).
- `bank-test-api-scenarios` / `bank-test-scenarios` payment settlement
  scenarios still pass (webhook → outbox → relay → `bank-payment` settles),
  proving the end-to-end path over real Pulsar + FDB.
- Full `clojure -M:poly test project:dev :all`.
