# 19. Processor packaging is deployment-time composition
<!-- tessl-plugin: design -->

## Status

Accepted.

## Context

Every command processor originally shipped as its own microservice:
a `X-processor` base (boilerplate `main.clj` plus a require
bundle) and a `X-processor-service` project (message-bus wiring plus
the domain's system YAML). Ten processor deployments existed, each
under-utilised — ten JVMs paying the FDB Record Layer and message-bus
client footprint to consume a trickle of commands, ten CI project
runs, ten Helm entries.

The unit of *code* was never the service. A processor's behaviour
lives in its `X` brick; the base contributes nothing but
component-kind registration, and the project's `application.yml` is
what actually composes a running system. The monolith already runs
every processor in one JVM. Packaging was per-domain out of
uniformity, not necessity.

## Decision

Processors are packaged by deployment-time composition: one thin
base per *service group* (boilerplate main plus a require bundle
registering that group's component-kinds), and one project per
group, whose `application.yml` alone decides which processors,
watchers, and event consumers that JVM hosts. The bases are
group-scoped rather than one shared superset so each project's
deps carry only the bricks its group runs — `poly check`'s
unnecessary-component warning stays meaningful for these projects
instead of being structurally silenced.

Grouping is by boundary, not throughput:

- **`financial-processors-service`** — payment, transaction,
  interest, payee-check. Operations that post, settle, accrue, or
  gate a payment.
- **`operational-processors-service`** — bank, party,
  cash-account, cash-account-product, idv. Operations that provision
  or verify the records money moves through; nothing posts to the
  ledger.
- **`scheduler-processor-service`** — unchanged. The Quartz
  runner is a per-JVM singleton timer; it stays alone so no group's
  replica count can ever double-fire a trigger.
- **External adapters and simulators** — unchanged, never grouped
  with domain processors. They own outbox/intent stores and webhook
  servers with their own lifecycles.

Financial and operational processors never share a JVM: a poison
message, memory spike, or deploy of a provisioning domain must not
sit in the same failure domain as money movement.

Two invariants when regrouping:

- **Cursor continuity.** message-bus consumer groups and FDB watcher
  `consumer-id`s move with the processor, verbatim. The subscription
  and changelog cursor identify the *consumer role*, not the pod
  that happens to host it; renaming one abandons a cursor and
  re-consumes or skips.
- **Replicas stay 1 per group** until watchers get leader election —
  changelog watchers are single-cursor consumers (the ADR-0008
  scale-out limitation). Command consumption via Shared
  subscriptions would scale horizontally; the watchers riding in the
  same JVM are what pin it.

## Consequences

Easier:

- Three processor deployments instead of ten; CI's per-project
  matrix and the Helm/Tilt/bake/release inventories shrink to match.
- Regrouping is configuration and plumbing, never brick code.
  Promoting a hot domain to its own deployment (or moving one
  between groups) relocates its YAML, message-bus wiring, bundle require,
  and deps — the `X` brick itself is untouched.
- A new processor no longer scaffolds a base and a service: it adds
  its brick, its `bank/X.yml`, and message-bus wiring to the group its
  boundary dictates.

Harder:

- Failure isolation within a group is gone by design — the financial
  group accepts that a payee-check DLQ storm shares a pod with
  payment consumption; the boundary rule keeps the blast radius on
  one side of the financial line.
- Per-domain resource attribution needs metrics, not `kubectl top`.
- Moving a processor between groups touches three places instead of
  one: the domain YAML and message-bus wiring move between projects, the
  brick moves between the two bases' require bundles, and the deps
  move between the two projects' `deps.edn`s.
