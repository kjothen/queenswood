# Crossplane providers

<!-- tessl-plugin: deployment -->

## Status

**Verified**, 2026-08-28, on this installation's plane: steps 1 to 3
were run against the GKE `Cluster` kind, step 4 listed the resources
whose cloud identifier is not their Kubernetes name, and step 5 against
`DNSAuthorization` gave the record the Discussion describes.

## Problem

You need to write a composed resource against a kind upjet generated
from a Terraform provider, whose CRD is not shaped like the Terraform
documentation.

## Solution

### Prerequisites

- A management plane running in the installation's folder, with the
  provider for the kind installed on it.
- Google group memberships, by capability:
  - Every step — `platformViewer`, e.g.
    `grp-gcp-<code>-platform-viewer@`.

```bash
# the installation code, e.g. qw01
export CODE=qw01
```

### 1. Find the kind, in the namespaced group

```bash
# the kind, as the CRD spells it, e.g.
export KIND=Cluster

just crossplane-explain "$KIND" apiVersion "$CODE-mgmt"
```

The first line is the resource this composes against. Both API groups
are usually installed, and the recipe resolves to the namespaced `.m.`
one; where there is no `.m.` kind it says so, and lists what it did
find.

### 2. Read the schema from the CRD

```bash
just crossplane-explain "$KIND" spec.forProvider "$CODE-mgmt"
```

Field shapes here are the ones that apply. Take a nested field the same
way, passing `spec.forProvider.nodeConfig` as the path, rather than
reading it from the provider's documentation.

### 3. Decide what to set, and what to leave

```bash
# an existing resource of the kind, as kind.group/name, e.g.
export OBJ="cluster.container.gcp.m.upbound.io/$CODE-n-test"

just crossplane-owners "$OBJ" "$CODE-mgmt"
```

What reads `provider` there is late-initialised: composed by nobody and
written back from the cloud. Compose such a field yourself where its
value is a choice somebody should make, or a parameter a later create
will need. Leave it where neither is true.

This needs a resource of the kind to already exist, and what a provider
late-initialises is only visible once one does. For the first of a
kind, compose what step 2 says is required, let it build, and come back
to this step before the second one is written — the fields to take are
the ones the provider filled in while you were not looking.

### 4. Decide the external name

```bash
just crossplane-external-names "$CODE-mgmt"
```

The external name is the cloud identifier and defaults to
`metadata.name`; this lists the resources where the two differ, which
is where something had to set it. Set it explicitly where the cloud
spells the id in characters a Kubernetes name cannot hold, or where the
resource has no field for its id at all.

### 5. Compose a value that only exists after create

```bash
just crossplane-explain "$KIND" status.atProvider "$CODE-mgmt"
```

What a kind publishes only once it exists. Where one of those is
another resource's input, pivot it up to the composite with a
`ToCompositeFieldPath` patch and compose from there rather than
committing a literal.

## Failures

**`SYNCED` `False` on a resource whose spec looks right.** A field
Terraform marks ForceNew. upjet declines rather than replacing, and
says so in `LastAsyncOperation` — `Synced` alone describes something
else entirely. Changing it is
[crossplane-live](crossplane-live.md); keeping callers away from it is
[crossplane-design](crossplane-design.md).

**An edit that never arrives, on a field nobody would call identity.**
Identity covers more than a name. A managed `Certificate`'s
`managed.domains` is ForceNew, so a second domain would replace the
certificate rather than extend it — which upjet declines to do, so the
domain simply never appears and the certificate goes on serving the one
it has. A node pool's `serviceAccount` and an IAM binding's `member`
are identity the same way, and a list that reads as extensible is the
easiest of the three to misjudge. Compose a second `Certificate`
instead: one `DNSAuthorization` covers a domain and its wildcard, so
the two share it rather than needing one each.

**A resource that never completes, on a create that succeeded.** The
external name is empty immediately after create for some kinds — a
`Project` is one — so the first build finishes only when the generated
id is fed back as an `adopt` value.

**A field that reverts, or one that vanishes when a patch is
removed.** Late initialisation. The provider owns what it wrote back,
and a composition that stops declaring a field it previously owned
drops it on the apply that relinquishes it. Which manager holds what is
[crossplane-debug](crossplane-debug.md).

**A provider whose Deployment disappears when another is installed.**
A name pinned in `serviceAccountTemplate`. The package manager creates
and takes controller ownership of the account it names, and Kubernetes
permits one controller owner — so a name shared between providers, or
held across one provider's own revisions, leaves every claimant but the
first failing its post-establish hook. Crossplane 2.3 tolerated it and
2.4 does not.

## Rules

**MUST:**

- Read the schema from the installed CRD with `just crossplane-explain`
  before writing a composed resource, never from the provider's
  documentation.
- Use the `.m.` API group.
- Check what the provider late-initialises, with
  `just crossplane-owners`, before deciding which fields to compose.
  Compose one whose value is a choice somebody should make, or a
  parameter a later create will need.
- Set the external name explicitly where it must differ from the
  Kubernetes name, or where something else spells it —
  `just crossplane-external-names` is where the two already differ.
- Feed a generated id back as an adopt value where the external name is
  empty after create, or the resource never completes.
- Pivot a provider-assigned value up to the composite and compose from
  it, rather than committing a literal read out by hand.
- Create a service account a provider shares, or that a binding names,
  outside the package manager — and point the pod at it with
  `deploymentTemplate`.

**MUST NOT:**

- Expect a ForceNew change to replace a resource. It is refused.
- Diagnose from `Synced` alone. The refusal is in
  `LastAsyncOperation`.
- Treat a list-shaped field as extensible without checking. A
  `Certificate`'s `managed.domains` is identity, so a second domain is
  refused rather than appended.
- Re-add a patch for a field late-initialisation now owns.
- Pin a name in `serviceAccountTemplate`. The package manager takes
  controller ownership, and the next claimant — another provider, or
  this provider's next revision — fails its runtime hook.

## Discussion

We read the CRD rather than the documentation because these providers
are generated: upjet wraps a Terraform provider, so the Go schema is
the source and the CRD is what it produced, while the Terraform
registry describes whatever version its page was written against. The
shapes diverge in both directions — a v1 list becomes a v2 map, and an
argument the registry documents turns out not to exist on the installed
CRD at all.

**What identity means here, and why it is not in the schema.**
Terraform marks a field ForceNew when changing it requires replacing
the resource, and performs the replacement itself. upjet declines, and
the flag does not survive into the CRD: 1,300 field descriptions on the
GKE `Cluster` kind mention replacement twelve times, every one of them
Kubernetes' own boilerplate or a GKE note about image types.
`managed.domains` describes itself as the domains a certificate will be
generated for, and says nothing about what adding one does. So which
fields are identity cannot be looked up, and is written down here
instead as it is found — a resource's name, a node pool's
`serviceAccount`, an IAM binding's `member`, a `Certificate`'s
`managed.domains`.

**Why a status value is pivoted rather than copied.** A
`DNSAuthorization` issues the validation record it wants answered —
name and data both — as `status.atProvider.dnsResourceRecord`, and only
once it exists. The value is assigned by the provider, differs per
resource, and a literal read out by hand is correct only until
something rebuilds it, so the records answering it are composed from
the composite rather than from what somebody pasted in.

**Why a pinned service account is not the package manager's to own.**
A `DeploymentRuntimeConfig`'s `serviceAccountTemplate` has the package
manager create the account it names, and each `ProviderRevision` claims
controller ownership of it. Naming is exactly what Workload Identity
and a RoleBinding need, so an account anything else refers to has to be
created by something else — a chart — and named at pod level through
`deploymentTemplate.spec.template.spec.serviceAccountName`, which the
package manager uses without claiming. `serviceAccountTemplate` is for
an account nothing else refers to, which is the case where the
generated one would have done.

**Why late initialisation is worth pre-empting.** A late-initialised
field looks exactly like a composed one in the spec, and
`metadata.managedFields` is the only place the difference shows. A node
pool's `upgradeSettings` unset means the provider writes back whatever
GKE defaulted to, so the spec reads as declared while nobody chose it,
and the next pool starts from whatever the defaults are by then. Its
`networkConfig.podIpv4CidrBlock` is the other kind: harmless while the
pool exists, and refused the next time one has to be made.

## References

- [crossplane-design](crossplane-design.md) — what cannot change after
  create, and where to fix it.
- [crossplane-live](crossplane-live.md) — changing an identity field on
  a resource that already exists.
- [crossplane-debug](crossplane-debug.md) — which field manager owns
  what.
- [crossplane-upgrades](crossplane-upgrades.md) — the packages these
  providers ship as.
- [gcp-iam](gcp-iam.md) — what the provider's identity needs.
