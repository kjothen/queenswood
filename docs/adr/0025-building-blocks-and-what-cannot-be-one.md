# 25. Building blocks, and what cannot be one

<!-- tessl-plugin: deployment -->

## Status

Accepted. Extends
[ADR-0016](0016-crossplane-over-terraform.md), which chose Crossplane
for cloud infrastructure, and
[ADR-0024](0024-instances-are-their-own-composites.md), which made an
instance its own composite.

How a kind is designed is a practice rather than a decision, and lives
in [crossplane-design](../recipes/infra/crossplane-design.md). What is
left here is the one part a recipe cannot carry: the boundary.

## Context

A catalogue of building blocks is an appealing idea. A bucket, a
database, a workload identity, each defined once and assembled by
whatever needs one, with a single place to fix a shape rather than
several to remember.

The question that outlives any particular catalogue is what it can
contain at all — and that one has to be settled before somebody spends
a day looking for a block that cannot exist.

## Decision

### The catalogue holds only what has an API

A kind needs a provider that can create the thing. Some of what an
installation depends on has no API at all: the organisation, domain
verification, the registrar's delegation, an OAuth client with a chosen
redirect URI. These are not missing from the catalogue because nobody
has written them yet. They cannot be in it.

That boundary matters more than the catalogue does, because "the only
way to build cloud infrastructure" is true of one half and false of the
other, and somebody will eventually go looking for the abstraction that
cannot exist. The manual half lives in recipes —
[gcp-secure-foundation](../recipes/infra/gcp-secure-foundation.md),
[cloud-dns](../recipes/infra/cloud-dns.md),
[google-sign-in](../recipes/infra/google-sign-in.md) — and is as much a
part of building an installation as anything composed.

Note what this does *not* exclude. The OAuth client cannot be composed;
the Secret Manager entry holding its secret is an ordinary managed
resource and belongs in the catalogue like any other.

## Consequences

**Easier.** A search that ends. Where a thing has no API, the answer is
a recipe rather than a kind nobody has got round to writing, and the
recipes that carry the manual half are named above.

**Harder.** Building an installation means holding two halves at once,
and only one of them reconciles. Nothing detects that the manual half
has drifted, because nothing is watching it.

**Deferred.** Whether the composed half becomes the *only* way to build
what does have an API. That is a policy, it costs the ability to
compose a one-off managed resource, and the instance's own Composition
still composes about twenty of them directly.

## References

- [ADR-0016](0016-crossplane-over-terraform.md) — Crossplane over
  Terraform.
- [ADR-0024](0024-instances-are-their-own-composites.md) — an instance
  is its own composite.
- [crossplane-design](../recipes/infra/crossplane-design.md) — how a
  kind is designed, which this no longer covers.
- [gcp-secure-foundation](../recipes/infra/gcp-secure-foundation.md),
  [cloud-dns](../recipes/infra/cloud-dns.md),
  [google-sign-in](../recipes/infra/google-sign-in.md) — the manual
  half.
