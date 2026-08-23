# 25. Building blocks, and what cannot be one

<!-- tessl-plugin: deployment -->

## Status

Proposed. Extends
[ADR-0016](0016-crossplane-over-terraform.md), which chose Crossplane
for cloud infrastructure, and
[ADR-0024](0024-instances-are-their-own-composites.md), which made an
instance its own composite. Nothing here is built. It records a
direction and, more usefully, a boundary — so that the first attempt at
a catalogue starts from a decision rather than from a preference.

## Context

Two composites exist and they repeat each other:

- `ProjectService` — ten on the plane, nine on the instance
- `ProjectIAMMember` — seven and eight
- `ServiceAccount` — six and four
- `ServiceAccountIAMMember` — five and three

Each written longhand, each roughly twenty-five lines of patch
boilerplate differing in an API name or a role. The instance's secrets
identity was copied from the plane's, by hand, and the copy carried a
mistake the original did not: a `projectRef` on a kind that declares
none, which server-side apply refused.

The appeal of a catalogue is obvious from that. A bucket, a database, a
workload identity, each defined once and assembled by whatever needs
one — and one place to fix a shape rather than two places to remember.

What is less obvious is which of that repetition wants a composite,
and what the catalogue can contain at all. Both questions have been
answered in passing, differently each time, and this settles them
before a catalogue makes the answers expensive to revisit.

## Decision

### Repetition alone is a templating problem

A composite that composes composites costs legibility. Crossplane
reports a failure on the composite rather than on the resource that
caused it, so a nested one reports two levels up from where anything
went wrong. That distance has already confused this project repeatedly
with a single level.

So nineteen `ProjectService`s want a `range` over a list of API names
in one templating step: no new kind, no second reconcile loop, no
status to propagate, and the same fix in one place. The same is true of
most IAM bindings.

A kind is earned by having a life of its own — something that can be
created, observed and destroyed independently of whatever asked for it,
and that has state worth reading back. `XQueenswoodInstance` earns it:
it carries `state: up | down`. A service account, on its own, does not.

### Both halves of a pair are one thing

The exception is where two resources must agree and half of one is a
silent failure. Workload Identity is the case: the GCP binding and the
Kubernetes annotation are separately valid and jointly required, and
granting one is a documented way to lose an afternoon.

Where a pair like that exists, a composite is worth its cost, because
what it buys is that the pair cannot be half-created — not that it is
written once.

### The catalogue holds only what has an API

A composite needs a provider that can create the thing. Some of what an
installation depends on has no API at all: the organisation, domain
verification, the registrar's delegation, an OAuth client with a chosen
redirect URI. These are not missing from the catalogue because nobody
has written them yet. They cannot be in it.

That boundary matters more than the catalogue does, because "the only
way to build cloud infrastructure" is true of one half and false of the
other, and somebody will eventually go looking for the abstraction that
cannot exist. The manual half lives in recipes —
[cloud-account](../recipes/infra/cloud-account.md),
[cloud-dns](../recipes/infra/cloud-dns.md),
[google-sign-in](../recipes/infra/google-sign-in.md) — and is as much a part
of building an installation as anything composed.

Note what this does _not_ exclude. The OAuth client cannot be composed;
the Secret Manager entry holding its secret is an ordinary managed
resource and belongs in the catalogue like any other.

### Cloud APIs are composed, cluster workloads are delivered

A catalogue of cloud building blocks does not make Crossplane the way
to install software on a cluster. That line is
[ADR-0024](0024-instances-are-their-own-composites.md)'s, not this
one's: the composite creates the identity a controller runs as, and
Argo installs the controller. A catalogue is a reason to keep it rather
than to cross it.

The practical case is the same as the principled one. `provider-helm`
has no provider configuration on the plane at all, and its only
`Release` is `Observe`-only, so composing a workload onto an instance
cluster would mean building a second delivery path beside one that
works.

## Consequences

**Easier.** One shape to fix rather than two to remember, and a pair
that cannot be half-created. A reader learning the infrastructure meets
a vocabulary rather than nineteen instances of a pattern.

**Harder.** Every kind in the catalogue needs its provider installed on
every plane that composes it, and a nested composite reports failures
further from their cause. A catalogue also invites the question of
whether composing a raw managed resource is still allowed, which this
does not answer.

**Deferred.** Whether the catalogue becomes the _only_ way to build
cloud infrastructure. That is a policy, it costs the ability to compose
a one-off, and it is worth deciding when there are more consumers than
the two that exist.

## Future

Which blocks come first, if any. On the evidence here it would be
workload identity, then the enablement of an API, then a secret and the
identity that reads it — the three that are already written twice.

## References

- [ADR-0016](0016-crossplane-over-terraform.md) — Crossplane over
  Terraform.
- [ADR-0024](0024-instances-are-their-own-composites.md) — an instance
  is its own composite.
- [crossplane](../recipes/infra/crossplane.md) — what a composite costs, and
  where failures report.
- [gcp-iam](../recipes/infra/gcp-iam.md) — both halves of Workload Identity.
