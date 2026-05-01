# 3. Message-bus abstraction with pluggable backends

## Status

Accepted.

## Context

Queenswood needs asynchronous messaging in three places:

- Commands from the HTTP API to processors (`bank-cash-account`,
  `bank-payment`, and so on).
- Replies from those processors back to the originating handler.
- Events between components — for example `transaction-settled` from
  the ClearBank adapter to the payment settlement code.

Several broker technologies are credible candidates: Apache Pulsar,
Kafka, RabbitMQ, NATS, AWS SNS/SQS, Google Pub/Sub. Each has its own
trade-offs in throughput, ordering guarantees, retention, replay,
schema support, and operational complexity. Locking the production
codebase into the API surface of any one of them would make future
changes — switching brokers, supporting more than one, running
in-process for tests — costly across every component that uses
messaging.

We also want to be able to run tests without spinning up a broker
container every time. An in-process backend that exercises the same
producer / consumer code paths but over Clojure channels is useful
for tests, REPL work, and small-footprint deployments.

## Decision

We will keep the message bus behind an abstraction. The `message-bus`
brick exposes two small protocols — `Producer` and `Consumer` — and
ships two implementations:

- `pulsar` — the production backend. We chose Pulsar because we had
  working code in `mono` and were already familiar with it. We are
  not committed to it; "we have it to hand" is the honest reason.
- A Clojure-channels backend (the `local` namespace inside
  `message-bus`), used in tests and small-footprint deployments.

Component code (`command`, `command-processor`, `event`,
`event-processor`, and so on) consumes the abstraction only. No
production component imports `pulsar` or `local` directly — backends
extend the `Producer` / `Consumer` protocols. The system definition
decides which backend a given producer or consumer binds to at
startup.

## Consequences

Easier:

- Backends are swappable. If Pulsar's licensing, performance, or
  operations story changes — or if Kafka or anything else turns out
  to be a better fit — swapping is a contained piece of work: write
  a new backend, rebind in the system config. Not a sweep across
  every component that uses messaging.
- Tests that do not specifically exercise the broker can run on the
  channels backend, no Testcontainers required. Faster, less flaky,
  and they cover the same producer / consumer code paths as
  production.
- The decision to use Pulsar is reduced to "what we have to hand."
  Future re-evaluation costs are bounded.

Harder:

- The abstraction must not leak. Pulsar-specific concepts —
  subscription modes, topic hierarchies, namespaces, message
  properties, delayed delivery — have to stay behind the backend
  boundary. This requires discipline when adding features.
- Two backends mean two test surfaces. Behaviour that holds on
  channels (ordered, in-process, no network) but fails on Pulsar (or
  vice versa) is a real risk. Production-shaped integration tests
  must run on the production backend.
- Some broker-specific features may not fit the abstraction cleanly.
  If we ever need, say, Pulsar's geo-replication or Kafka's exactly-
  once semantics, we either extend the interface (burdening every
  backend) or accept it as backend-only — neither is free.

The abstraction comes from `mono`; see ADR-0001. Future improvements
(e.g. adding a Kafka backend) should be made upstream where possible.
