# Plan: carry the trace across the changelog and outbox

Join the spans either side of an async hop, so a payment settling shows
in the same trace as the request that started it.

## What is broken

Measured over one `test-api-scenarios` run, 839 spans:

- **170 of 195 `process-command` spans** share a trace id with a server
  span. Those are dispatched straight from the API, and traceparent
  survives HTTP → Kafka → processor intact. This path already works.
- **25 `process-command` spans** do not. Every one is `submit-idv-check`
  or `submit-payment` with a `causation_id` naming an entity rather
  than a request — watcher-dispatched, reacting to a changelog entry.
- **All 181 `process-event` spans** do not. Events are relayed from the
  outbox, and nothing carries a parent into them.

So a trace stops at the first async hop. `correlation_id` and
`causation_id` still chain across it, which is why the causal story is
recoverable today — but only by grepping ids, not in a span tree.

## Why it stops

The consumer side is already built. `changelog-relay/consumer.clj`
calls `telemetry/extract-parent-context` on every event and opens
`process-event` under whatever parent it finds.

It never finds one, because **nothing calls `inject-traceparent`**.
mono exports it; no Queenswood caller exists. There is also nowhere to
put the result: no changelog or outbox record has a field for it.

## Two halves, very different sizes

The protos split into two families, and the two orphan cases sit on
opposite sides of that split.

### Half one — events (181 of the 206 orphans)

`ChangelogEvent`, `ClearbankOutboxEvent` and `OnfidoOutboxEvent` share
one shape by design: fields 1–7 identical and wire-compatible, so an
adapter outbox entry decodes as a `ChangelogEvent`. That is the shape
`changelog-relay/envelope.clj` decodes and republishes.

- Add `optional string traceparent = 8` to all three, keeping them
  aligned. Additive, so entries already on a cursor still decode.
- Capture at write time. `save-event` runs on the request thread inside
  the FDB transaction, so `(telemetry/inject-traceparent)` there picks
  up the live span with no threading.
- In `envelope.clj`, put it on the published envelope under the string
  key `"traceparent"` — `extract-parent-context` takes string keys, and
  the envelope is currently keyword-keyed, so check that hand-off.

Bounded: three protos, two `save-event` call sites, one relay file.

### Half two — watcher-dispatched commands (25)

Watchers read a *per-domain* changelog — `BankChangelog`,
`CashAccountChangelog`, `IdvChangelog`, `PartyChangelog` — which share
no shape with each other or with the envelope family, and have no spare
aligned field number.

Each would need its own `traceparent` field, and each watcher would
need the parent threaded from the entry it is reacting to into the
envelope it builds (`idv/core.clj`, `payment/core.clj`). Wider, fiddlier
and worth a quarter of the benefit.

Worth asking first whether these *should* join the request's trace.
Under ADR-0008 a watcher fires because a record changed, not because
someone called an endpoint; a span tree that shows one request fanning
into every downstream reaction it eventually caused may be the honest
picture, or may be a tree nobody can read. Half one does not depend on
answering that.

## Steps

1. Add the field to the three envelope-family protos, then
   `clj -X:deps prep :aliases '[:dev]' :force true` — a proto change
   that is not regenerated fails at full-system startup, not at compile.
2. Capture in `save-event` on both relays; assert it lands by reading
   the record back.
3. Publish it from `envelope.clj`, minding the string-vs-keyword keys.
4. Tighten the existing assertion in
   `test-api-scenarios/interface_test.clj`: `process-event` spans should
   now share a trace with a server span, so the orphan count for events
   becomes zero rather than 181.
5. Decide on half two separately.

## Verification

The assertion added in `86e5595b` is the harness. Extend it to count
orphans per span name and require zero for `process-event`; the same
probe that produced the numbers above becomes the test.

## Not in scope

- Sampling. Every request is traced today; joining traces across hops
  makes them longer, not more numerous.
- `process-command` orphans, until half two is agreed.
