# 25. Building blocks, and what cannot be one

<!-- tessl-plugin: deployment -->

## Status

Accepted. Extends
[ADR-0016](0016-crossplane-over-terraform.md), which chose Crossplane
for cloud infrastructure, and
[ADR-0024](0024-instances-are-their-own-composites.md), which made an
instance its own composite.

Four kinds are built, all extracted from `XQueenswoodInstance`, which
halved: `XPostgres`, `XNetwork`, `XPublicEndpoint` and `XCluster`. The
sections below that were written before any of them still hold; the
four after them are what extracting taught, and none was visible from
the design. What is left is in
[composite-catalogue](../plan/composite-catalogue.md), and what a
transfer mechanically does is in
[crossplane](../recipes/infra/crossplane.md).

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

### What a kind is named for is not what it owns

The first extraction attempted was the step composing an instance's
backups, chosen because it was bounded, sat in a pipeline step of its
own and already published a status contract. It was the wrong seam. The
bucket it owns is written by FoundationDB and by Keycloak's realm
export, under one identity, deliberately — so a kind named for either
consumer would have had to break a store the other depends on, and the
lifecycle rules on that bucket name a prefix belonging to neither.

The same fault appeared inside a kind rather than in the choice of one.
`XPostgres` was extracted as a server hosting many databases and
composed exactly one, named `keycloak` in a format string. The
justification and the code disagreed and nothing failed, because a kind
that can only serve its first caller still serves it.

So the question is what a candidate owns, not what it is called or
where its code sits. Boundedness is a property of how something was
written; ownership is a property of what it is.

### Lifecycle separates what cohesion joins

Two things can belong together and still need separate kinds, when one
is durable and the other disposable. A public zone and a public
endpoint are the case: an endpoint's address and certificates rebuild
from their own declaration, while a recreated zone gets nameservers the
registrar does not follow and draws from a finite per-domain pool. A
network and a cluster are the same shape — GCP refuses to delete a
network while a cluster sits in it.

A composite is a unit of replacement, so composing both from one kind
puts a resource that must never be deleted beside ones rebuilt
routinely. ADR-0022 already drew that line for resources; it draws it
for kinds too.

### Cohesion and transferability are different questions

The best-cohesion candidate in the composite was its ingress: a
certificate and its DNS authorization are exactly the pair this ADR
says earns a kind, since half of one is a silent failure. It was also
the most expensive to extract, because everything in it carries
`Delete` — moving ownership would have released the address every
record points at and destroyed the certificate the endpoint terminates
on.

ADR-0022's tiering inverts the two. The durable tier withholds `Delete`
and is cheap to move; the disposable tier carries it and is not. So the
resources designed to be rebuilt freely are precisely the ones whose
*ownership* cannot be moved without rebuilding them, and "what should
be a kind" and "what can safely become one" are separate questions that
happen to have opposite answers here.

### Name the thing, at the most concrete level that is true

Two rules, pulling against each other, and the tension is the useful
part.

Do not name a kind for what consumes it. A composite registering a
cluster with Argo was first called `XArgoCluster`, which parses as a
cluster belonging to Argo — Argo is not a cluster, and the cluster
already has a kind. A kind named for its reader also inherits that
reader's lifetime.

But do not reach for the generic wrapper either. `XPostgres` is the
concrete engine where `XCloudSQL` would be the category, and the
concrete name is the better one. A name that sounds portable claims a
portability the composition has not got.

What resolves them is naming the thing itself, as concretely as remains
true of it, and borrowing the vocabulary of whatever domain it belongs
to rather than inventing a synonym — an Argo Application has a
`destination`, so `XArgoDestination` is a word that already means this.

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

This guessed at workload identity, API enablement, and a secret with
the identity reading it — the three written twice at the time. What
came first instead was a Cloud SQL server, a network, a public endpoint
and a cluster, because the pressure was the length of one composite
rather than the count of a repeated pattern. The guess was not wrong so
much as answering a different question, and API enablement did arrive —
as a loop, which this ADR had already said was the right shape for it.

What is left, and in what order, is in
[composite-catalogue](../plan/composite-catalogue.md).

## References

- [ADR-0016](0016-crossplane-over-terraform.md) — Crossplane over
  Terraform.
- [ADR-0024](0024-instances-are-their-own-composites.md) — an instance
  is its own composite.
- [crossplane](../recipes/infra/crossplane.md) — what a composite costs, and
  where failures report.
- [gcp-iam](../recipes/infra/gcp-iam.md) — both halves of Workload Identity.
- [composite-catalogue](../plan/composite-catalogue.md) — what was
  built and what is left.
