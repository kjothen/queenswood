# 8. Changelog watchers for reactive state transitions

## Status

Accepted, with a known scale-out limitation. See "Future" below.

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

The watcher primitives are inherited from `mono` (ADR-0001); the
choice to use them rather than message-bus events is a Queenswood
application-level decision, made per flow at system-definition time.
