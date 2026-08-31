# Plan: split the folder from the plane, and name what is left

## Context

[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
decided that a GCP folder is what an installation is, and
[ADR-0024](../adr/0024-instances-are-their-own-composites.md) that an
instance is its own composite beside `XManagementPlane`. Between them
the folder ended up inside the plane's composite, and it does not
belong there.

Nothing here is written. It needs an ADR superseding ADR-0022's central
decision before any of it lands, because it retires the word
*installation* along with the shape.

## Before

```
Organisation              manual
  prj-b-seed              manual + seed identity
  fldr-<code>          ┐
  org-policy exemption |
  folder IAM           |  XManagementPlane   <- spans two lifetimes
  prj-<code>-c-mgmt    |
  cluster, Argo, XP    |
  identities, proj IAM ┘
  prj-<code>-c-recovery
  prj-<code>-<env>-<label>  XQueenswoodInstance
```

The folder is never deleted; the cluster is rebuilt routinely. One
composite holds both, which is what
[crossplane-design](../recipes/infra/crossplane-design.md) forbids:
never compose resources with different deletion criteria into one kind,
since deleting a kind deletes what it composed.

## After

```
Organisation                        manual: directory, billing, org policy
  prj-b-seed                        manual + seed identity, org-scoped, once
==================================  org | subsidiary
  fldr-<code>                       XSubsidiary
  org-policy exemptions               spec: code, parent, access
  folder IAM from access              status: folderId
==================================  subsidiary | plane
  prj-<code>-c-mgmt                 XManagementPlane
  cluster, Crossplane, Argo           spec: folderId, access
  platform/argo/secrets identities
  project IAM from access
==================================  plane | unit
  prj-<code>-<env>-<label>          XQueenswoodInstance
```

The handoff boundary is `XSubsidiary`'s status: a folder with its
capabilities already bound.

## What the split buys

**A branch becomes a boundary.** The bootstrap recipe carries a
conditional — where a platform team hands you a folder, steps 1 to 3
are theirs and you set `createFolder.folderId` rather than creating
one. Split, that case is somebody else having run the same composite
and given you its `folderId`. One artefact, two suppliers, no branch.

**The seed's folder rights close sooner.** Today the seed identity
holds `folderCreator`, `folderIamAdmin` and `projectCreator` across the
whole run. Split, the folder rights are wanted for one composite and
the project rights for the next, so the organisation-scoped grant can
be revoked as soon as the folder exists rather than at the end.

**The blast radius shrinks.** A composite is a unit of replacement, and
the plane's currently contains the one object nothing may delete.

## Groups are bound here and created elsewhere

`XSubsidiary` composes the bindings and takes the principals as input,
through the same `access` mapping ADR-0023 defines. Creating a group
stays a directory act.

The reason is escalation rather than convenience. Creating a group
needs a Cloud Identity admin role, and Groups Admin is not scopable to
a name prefix — an identity that could mint
`grp-gcp-<code>-platform-viewer@` could add itself to
`grp-gcp-org-admin@`, which carries Organization Administrator. That
would put an escalation path into the one identity the whole bootstrap
impersonates.

[organisation-foundation](../recipes/infra/gcp-secure-foundation.md)
currently gives a different reason — that a Cloud Identity write needs
a quota project and none exists. That is true when it is written and
false by the time a subsidiary is created, since `prj-b-seed` exists by
then. Correct it whether or not this plan proceeds.

`access` keeps taking whole IAM member strings, so an established
organisation answers each capability with its own group, a user, or a
`principalSet://`.

## The word

*Installation* is a nominalised verb that ADR-0022 redefines as a
place. That is why there is no verb left for the recipe that finishes
one — `installation-install` stutters, and `deploy`, `provision` and
`launch` each collide with what the bootstrap recipe already does.

A plain noun frees the verb slot. `subsidiary` carries the sense of an
independent, identically shaped unit inside an organisation, which is
what a folder is here. Against it: in core banking a subsidiary is a
legal entity, and `fldr-qw01` could be read as one. Worth weighing
`tenant`, `estate`, `landing-zone` — the industry term for exactly this
— and `cell` before committing.

Names that follow, replacing the `queenswood-` prefix that says nothing
in a repository where everything is Queenswood:

```
organisation-foundation     the org, billing, directory groups
gcp-bootstrap               prj-b-seed and the seed identity
subsidiary-create           the folder
subsidiary-boot             the management project, cluster and plane
subsidiary-install          what the plane offers, if it survives below
instance-deploy             one environment's project and the bank on it
instance-rebuild-cluster
up-and-running              the order they go in
```

Alongside: `cloud-*` becomes `gcp-*`, since `gcp-iam` and `cloud-dns`
name the same kind of thing under two prefixes; and `cloud-naming` and
`cloud-identifiers` move to `practices/`, being writing conventions
rather than infrastructure procedures.

## Open: does `subsidiary-install` survive

Its four parts may each belong somewhere else once the layers separate:

- **The Argo credential** looks like part of the boot. Without it the
  plane reconciles from nothing while reporting healthy, which is an
  incomplete boot rather than a later step.
- **The environment config** is cluster-scoped and holds what is true
  of the whole subsidiary, so it may belong to `XSubsidiary`.
- **The recovery project** is folder-tier and durable, so likewise.
- **The zone and its delegation** are a browser and a registrar, and
  [gcp-dns](../recipes/infra/cloud-dns.md) and
  [gcp-dns-delegation](../recipes/infra/cloud-dns-delegation.md)
  already own most of them.

If all four move, the recipe dissolves rather than being renamed.
Answering this is the same as answering what `XSubsidiary` composes, so
the ADR settles both or neither.

## Ordering

1. The ADR, superseding ADR-0022's folder-is-an-installation decision
   and recording the split, the word, and where groups are created.
2. `XSubsidiary` and the `XManagementPlane` change, which is a live XRD
   split — [crossplane-live](../recipes/infra/crossplane-live.md) is
   what that costs.
3. The recipe renames, which are cheap and entirely consequential on
   step 1.

## References

- [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation, which this supersedes
- [ADR-0023](../adr/0023-installation-naming-and-access.md) — the code,
  the capabilities, and the `access` mapping this keeps
- [ADR-0024](../adr/0024-instances-are-their-own-composites.md) — the
  instance as its own composite, which this follows for the folder
- [composite-catalogue](composite-catalogue.md) — the extractions out
  of `XQueenswoodInstance`, which this is the same move for the plane
- [crossplane-design](../recipes/infra/crossplane-design.md) — the
  deletion-criteria rule the current shape breaks
