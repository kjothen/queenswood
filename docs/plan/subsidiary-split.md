# Plan: split the folder from the plane, and name what is left

## Context

[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
decided that a GCP folder is what an installation is, and
[ADR-0024](../adr/0024-instances-are-their-own-composites.md) that an
instance is its own composite beside `XManagementPlane`. Between them
the folder ended up inside the plane's composite, and it does not
belong there.

The shape is Google's option 2, *"hierarchy based on regions or
subsidiaries"* — a first level of subsidiary folders beside a bootstrap
folder, each operating independently. See
[decide-resource-hierarchy](https://docs.cloud.google.com/architecture/landing-zones/decide-resource-hierarchy#option2).
The kind does not require it: it needs a folder and nothing more, so a
folder handed over under Development or Production in the guide's
[best-practice hierarchy](https://docs.cloud.google.com/architecture/landing-zones/decide-resource-hierarchy#best_practices_for_resource_hierarchy)
works identically — which is what `parent` taking `folders/` as well as
`organizations/` is for.

`XSubsidiary` is written and renders; nothing else here is. It needs an
ADR superseding ADR-0022's central decision before the live extraction,
because it retires the word *installation* along with the shape.

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

## The XR is the handover

`XSubsidiary` is what crosses the boundary, in both directions:

- **Ours** — no `folderId`; it composes `fldr-<code>` and the bindings.
- **Handed a folder** — `folderId` set; it adopts and records the one
  you were given, keeping their `displayName` and `parent`.

Both leave the same object for `subsidiary-boot` to read, so the branch
that today sits at step 5 of an eleven-step bootstrap becomes one field
in a manifest. It is the convention the installation manifest already
uses — *"Supply `management.projectId` always and
`createFolder.folderId` wherever the folder already exists"* — moved
onto the composite it belongs to, and a spec field rather than a
`managementPolicies` difference so the mode is reviewable in the diff.

`parent` and `displayName` are required in both modes. The provider
holds `Update`, so either left to a default is one it would write onto
a folder somebody else owns — the first bug the rendered composition
turned up.

This also settles where the folder id lives. Only `XManagementPlane`
needs it: ADR-0024 already has an instance *"naming the composed
`Folder` as `fldr-<code>` from the instance's own `spec.code`, never by
referring to the plane's composite"*. So the `EnvironmentConfig` keeps
what it holds today and never carries the subsidiary's identity. One
object owns that, and no second copy can disagree with it.

## What is built

- [xsubsidiary-xrd.yml](/infra/platform/crossplane-xrds/xsubsidiary-xrd.yml)
  — `code`, `parent`, `displayName`, optional `folderId`, and an
  `access` mapping holding only the two capabilities that bind on a
  folder. `platformAdmin` binds on a service account and `secretsAdmin`
  on a project, neither of which exists until a plane is built there.
- [xsubsidiary-composition.yml](/infra/platform/crossplane-xrds/xsubsidiary-composition.yml)
  — the `Folder`, lifted from `XManagementPlane` with its
  `lifecycleState` readiness check, and the folder bindings through
  `function-go-templating`. `Delete` withheld from the folder in every
  mode; carried on a binding, so a principal dropped from `access`
  loses it in GCP.

`crossplane render` gives eight resources for a full mapping — six
`platformViewer` roles, one `clusterAdmin`, and the folder — and the
adopt path renders their id, their name and their parent.

**Not yet extracted from `XManagementPlane`.** Both kinds would compose
`fldr-<code>`, and two composites claiming one managed resource makes
every apply fail. Loading an XRD with no composites of its kind is a
known-safe state, so the file lands ahead of the move; the move itself
is [crossplane-live](../recipes/infra/crossplane-live.md)'s two-merge
transfer, and it is step 2 below.

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
organisation-foundation     the org, billing, directory groups     ours only
gcp-bootstrap               prj-b-seed and the seed identity       ours only
subsidiary-foundation       the access groups, in the directory    ours only
subsidiary-create           the XSubsidiary: compose or adopt      both modes
──────────────────────────  identical in both modes from here ─────────────
subsidiary-boot             the management project, cluster, plane
subsidiary-install          what the plane offers, if it survives below
instance-deploy             one environment's project and the bank on it
instance-rebuild-cluster
up-and-running              the order they go in
```

`subsidiary-foundation` comes **before** `subsidiary-create`: the
folder composite binds the capabilities, and IAM rejects a binding to a
principal that does not exist.

The seam moves somewhere better. Today it is a branch inside bootstrap
at step 5 — *"Where a platform team hands you a folder, steps 1 to 3
are theirs"*. After the split it is a line between two recipes, and
`up-and-running`'s two paths become "start at 1" or "start at 4" with
no recipe that is read rather than followed.

Alongside: `cloud-*` becomes `gcp-*`, since `gcp-iam` and `cloud-dns`
name the same kind of thing under two prefixes; and `cloud-naming` and
`cloud-identifiers` move to `practices/`, being writing conventions
rather than infrastructure procedures.

## A generated value is read, not transcribed

The plane composes things an instance must name: the Argo service
account it grants `container.admin` to on its own project, and the
recovery project whose suffix its backups bucket borrows.

> The plane's own comment on that identity says the instance grants it
> `container.developer` — *"it reaches Kubernetes objects inside a
> cluster and cannot create or delete the cluster itself"* — and the
> instance binds `container.admin`. One of the two is wrong, and the
> comment is the one making the least-privilege argument. Worth
> settling separately from any of this. Both reach
the instance today by being written into `environment.yml` — rendered
by a recipe that discovers them from live GCP, and committed.

That is the wrong direction. A plane that creates a resource and then
requires somebody to transcribe its identity into a file, so that
another composite can be told about it, produces authoring mistakes
rather than preventing them. The value already exists on the plane; the
instance should read it.

### What already works, and where it stops

Anything an instance *targets* goes by reference to a deterministic
Kubernetes name, and needs no file: `folderIdRef` to `fldr-<code>`,
`projectRef`, `serviceAccountIdRef`, `managedZoneRef`. That is the
mechanism ADR-0024 settled on for the folder.

It stops at two places:

- **A `member` string.** `ProjectIAMMember.forProvider` is `condition,
  member, project, projectRef, projectSelector, role` — read off the
  live CRD. `member` has no `Ref` and no `Selector`, because it may be
  a user, a group, a domain or a `principalSet://`, none of which is a
  Crossplane object.
- **A resource name.** The backups bucket is
  `bkt-<name>-backups-<suffix>`, where the suffix is split off the
  recovery project's id so that a rebuilt project does not rename the
  bucket. A name is a string, not a reference.

### The preferred fix

Install `function-extra-resources` and let the instance fetch what the
plane composed, by the same deterministic name it would reference:

```yaml
- step: extra
  functionRef:
    name: function-extra-resources
  input:
    spec:
      extraResources:
        - into: argoSA
          apiVersion: cloudplatform.gcp.m.upbound.io/v1beta1
          kind: ServiceAccount
          type: Reference
          ref:
            name: sa-<code>-c-argo
```

The instance already runs `function-go-templating`, so it spells the
member from what it read. The same fetch answers the recovery project.
`argoServiceAccount` and `recoveryProjectId` then leave
`environment.yml` entirely, which is what leaves that file holding only
facts somebody authored.

The composite may carry the Kubernetes name as a defaulted field, for
an installation that has to override it. Unlikely to be wanted: the
name is derived from the code, which is the thing that makes it
findable at all.

### What it costs

- A fifth function on the plane, and one more thing
  [crossplane-upgrades](../recipes/infra/crossplane-upgrades.md) checks
  is healthy afterwards.
- A different failure. The environment patch is deliberately not
  `Required` — *"a merge here before the key exists there would fail
  every instance rather than this one binding"* — and an extra resource
  that cannot be found may fail the whole pipeline step. The soft
  failure was chosen; whatever replaces it has to be chosen too.
- A line ADR-0024 drew. It says an instance finds the folder *"never by
  referring to the plane's composite, so neither composite knows about
  the other"*. Reading a resource the plane composed, by name, is the
  same distinction that already makes `folderIdRef` acceptable — but it
  is the ADR's line, and it should be drawn deliberately rather than
  crossed quietly.

### What `environment.yml` is left holding

```
manifestRepoURL      static config
billingAccountId     an account that pre-exists
access               who holds which capability
folder facts         folderId, or parent and displayName
```

All authored, none generated, and `access` stated once for the plane,
the subsidiary and every instance rather than in each.

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

0. **Done** — `XSubsidiary`'s XRD and Composition.
1. **Done** —
   [ADR-0027](../adr/0027-the-folder-is-a-subsidiary.md), superseding
   ADR-0022's folder-is-an-installation decision.
2. **Done** — the folder extracted from `XManagementPlane` and adopted
   by `XSubsidiary`, in the order
   [crossplane-live](../recipes/infra/crossplane-live.md) requires:
   `Delete` withheld in a merge of its own, then the transfer. No
   binding left GCP at any point. `createFolder` came out of the XRD,
   the manifest and the renderer after it.
3. **`queenswood-bootstrap` no longer describes what happens.** Its
   step 5 renders an installation manifest with no folder and step 6
   says the apply reports one. Nothing renders or applies a
   `subsidiary.yml`. A second installation following it would get a
   plane with nowhere to put its projects. The fix is two commands in
   step 5, one sentence in step 6, and `gcp-boot-mgmt-apply` reading
   two files — no new step.
4. `function-extra-resources`, independent of the rest and worth doing
   on its own merits: it is what stops a generated value being
   transcribed by hand.
5. `access` into `environment.yml`, once 4 has emptied that file of
   generated values.
6. The recipe renames, consequential on 1 and blocked only on whether
   `subsidiary-install` survives.

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
