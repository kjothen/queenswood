# 8. Changelog watchers for reactive state transitions
<!-- tessl-plugin: design -->

## Status

Superseded in part. The relay path described in "Future" is now the
direction of travel: it is generic, it runs in its own single-writer
service, and internal reactive flows are migrating onto it. Watchers
remain in place for the flows not yet moved — see "Future" below.

## Context

Some state transitions in Queenswood are triggered by other state
transitions:

- Identity verification completes → the party transitions from
  `pending` to `active`.
- A close request lands on a cash account → the account transitions
  to `closed` and balances freeze.
- A payment is created → downstream booking happens.

We need a reactive primitive — something that fires *after* a write
commits and drives the next thing. ADR-0002 already established that
FDB's changelog cursor *is* a transactional outbox: every committed
write appears on the cursor in commit order, atomically with the
write. The question this ADR answers is how we consume it.

Two architectural options are credible:

- **In-process changelog watchers.** A watcher component reads the
  per-store cursor directly, dispatches each change to a handler
  function in the same JVM, and persists its cursor position so it
  can resume after restart. Handlers see the proto-encoded record
  straight from FDB.
- **Event-based on the message bus.** Each component that owns
  writes publishes domain events on the message bus (ADR-0003) at
  commit time; subscribers react via the broker. To do this safely
  we'd reimplement an outbox relay per component — read the
  changelog, publish to the bus, advance our cursor — which is what
  ADR-0002 means when it says the transactional outbox pattern
  "needs deliberate per-service plumbing" elsewhere.

## Decision

We will use in-process changelog watchers for reactive state
transitions, for now. Each watcher runs as a system component
declared alongside the rest of the system definition (ADR-0007),
reads from a specific record store's cursor, and dispatches to
handler functions defined in the component that owns the source
record.

The decision is reversible per flow: a watcher-driven flow can be
replaced with an event-based one by giving the watcher a single
job — read the changelog, publish to the message bus — at which
point downstream consumers move to the message-bus subscriber
model. We have not needed to do this yet.

### Brick boundaries: react, don't orchestrate

Watchers also encode a rule about cross-brick coordination.
When a flow needs N bricks to react to one HTTP request, the
shape is N watchers — each in the consuming brick — not one
HTTP-layer orchestrator that chains commands across bricks.

- The HTTP layer (`api`) stays ignorant of downstream
  effects. POST `/v1/parties` dispatches `create-party` and
  returns. It does not know IDV exists, does not chain
  commands, does not coordinate.
- Cross-brick reactions go through the changelog. Brick X
  writes its records; FDB emits a changelog entry; brick Y's
  watcher reads it and acts on its *own* records. Brick X
  does not reach into brick Y; brick Y does not write
  brick X's records.
- A watcher must be **idempotent** — check existing state
  before acting, so changelog replay or duplicate triggers
  don't create duplicate records.

Bricks are the unit of independence in the Polylith. If
`api` chains commands across bricks, every new brick adds
an HTTP-layer change. If brick X writes brick Y's records, the
dependency graph cycles. Watcher-on-changelog keeps the
contract narrow: "I publish my changes; you decide what to do."

The trade-off — no end-to-end reply path, harder to audit a
single request's full effect — is a known and accepted cost.
The HTTP request acks only its immediate effect; the rest
unfolds asynchronously.

Synchronous in-handler work is appropriate when the caller
genuinely needs the effect to be on disk before the 200
returns — payment booking is the canonical example, where
the debit and the suspense write are atomic with the
response. Settlement is then *one* downstream watcher. IDV
and party activation don't have that constraint, so they stay
watcher-driven all the way down.

## Consequences

Easier:

- Reactive flows are simple. Define a watcher handler in the
  component that owns the source record. No event schema, no
  separate publish step, no outbox plumbing.
- ADR-0002's changelog-as-outbox property lands as a real
  architectural benefit. Without watchers we would reimplement
  outbox-relay logic in every component that needs reactive
  behaviour — exactly the per-service plumbing ADR-0002 said FDB
  saves us.
- Handlers run in the same JVM as the watcher; no broker hop.
- One less wire format on the reactive path. Handlers see the proto
  record from FDB, not a separately-defined event payload.
- Restart-resume is built in. A watcher persists its cursor, so a
  restarted JVM continues from where it left off.

Harder:

- **Watcher processors cannot scale horizontally without leader
  election.** Two watchers on the same cursor double-process every
  change. Scaling out would mean adding leader election (an FDB
  lease, an external coordinator, or similar) — the infrastructure
  cost we have so far chosen to avoid. The message-bus option, by
  contrast, gives horizontal scale via shared subscriptions or
  partitioned consumers without extra plumbing. Today this is fine —
  Queenswood runs as a single deployment — but the constraint is
  real and would bite in an active-active topology.
- Watcher-driven flows are invisible to anything outside the JVM.
  There is no event log on the message bus to inspect, replay, or
  fan out to a different system.
- Watchers are coupled to FDB. A flow that ever needs to react to
  events from a non-FDB source cannot use this pattern; it has to
  consume from the message bus instead.
- Per-flow reversibility helps, but introduces a small ongoing
  decision tax: each new reactive flow has to choose watcher vs
  message-bus. The default is watcher; the rule for promoting to
  message-bus is "we need scale-out, cross-system fan-out, or an
  externally-inspectable event log."

## Future

The likely path if scale-out becomes a constraint is *not* to
abandon watchers, but to give them the narrow job of relaying to the
message bus. Watcher reads cursor, publishes to bus, advances cursor;
downstream consumers move to the broker. This is the outbox-relay
pattern with the added property that the outbox is FDB's changelog
itself — exactly the property ADR-0002 highlighted. Implementing
leader election for the relay watcher is then the only piece of new
infrastructure required.

**Update: the relay is now a generic tier in its own service.** The
`changelog-relay` component is a config-driven runner — it tails one
store's cursor and republishes each entry, holding no domain logic — and
`relay-service` hosts every runner. The ClearBank and Onfido relays moved
there first; internal reactive flows (party activation, cash-account
close) still use in-JVM watchers and are migrating store by store. See
[transaction-processing.md](../tdd/transaction-processing.md) and
[payments.md](../tdd/payments.md).

Three corrections to the reasoning above, all verified against the code:

- **The relay does not dedupe.** `changelog/process` defaults to
  latest-entry-per-record-id, and the record-id is the entity id. A relay
  carries transitions, so collapsing two transitions committed inside one
  poll window would silently drop an event. The runner passes
  `{:deduplicate? false}`.
- **Leader election buys failover, not throughput.** A cursor has exactly
  one owner, but the relay's work is decode-and-publish — O(1) per entry,
  no business logic. `replicas: 1` plus a durable cursor already survives
  a crash; leader election would only shorten the gap before someone
  resumes. The relay tier scales by sharding stores across deployments,
  not by adding replicas.
- **Extracting the relay does not by itself deliver scale-out.** It
  removes the hard blocker — a cursor owner cannot be replicated at all —
  but every topic is currently single-partition and `message-bus/send`
  passes no partition key, so extra consumer replicas are idle standbys.
  Raising partition counts without first adding a key would lose
  per-entity ordering. Real horizontal throughput is gated on that key.

The watcher primitives are inherited from `mono` (ADR-0001); the
choice to use them rather than message-bus events is a Queenswood
application-level decision, made per flow at system-definition time.
