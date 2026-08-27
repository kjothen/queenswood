# Argo CD health

<!-- tessl-plugin: deployment -->

## Problem

You want Argo's health verdict to be true for the kinds a plane serves.

## Solution

Register a check for every group whose verdict anything acts on, and
carry corrected copies of the two Argo ships for Crossplane until the
fix for them reaches a release. They live in
`infra/helm/management-plane/templates/argocd-cm.yaml`, in one
`resource.customizations` block rather than as
`resource.customizations.health.<group>_<kind>` keys — two of the four
are wildcard groups, and a ConfigMap key cannot contain a `*`.

### Prerequisites

- A management plane running in the installation's folder.
- `platformViewer`, e.g. `grp-gcp-<code>-platform-viewer@`.

```bash
# the installation code, e.g. qw01
export CODE=qw01
```

### What to register

- **`<group>/*` per composite group**, from `compositeGroups` in the
  chart's values. An XRD loaded onto a plane whose group is not in that
  list is a composite that can fail to compose and read as though it
  worked.
- **`argoproj.io/Application`**, where a parent holds children in
  waves.
- **`*.crossplane.io/*` and `*.upbound.io/*`**, corrected copies of
  what Argo compiles in.

### The two copies, and when they go

Argo's compiled-in checks read as `A or (B and C)` where `(A or B) and
C` was meant, so a resource with no status answers Healthy from the nil
branch before the list of status-less kinds is consulted. The copies in
the chart are the corrected scripts from `argoproj/argo-cd#29382`,
transcribed rather than adapted so the diff against upstream stays
readable.

They are `resource_customizations/_.crossplane.io/_/health.lua` and its
upbound twin upstream. A `_` path segment is how that tree spells the
wildcard a directory name cannot carry, so `_.crossplane.io/_` is the
`*.crossplane.io/*` entry here — the same wildcard in a third
encoding, since a ConfigMap key cannot express it at all.

They are temporary. When that fix reaches a release this plane runs,
delete both entries and point `HAS_NO_CONDITIONS` in
`scripts/crossplane-statusless-kinds.py` at the upstream lists.

### Kinds that carry no status

```bash
just gcp-plane-statusless-kinds
```

It reads the plane for kinds no served CRD version gives a `status`
subresource or a `status` property, and diffs them against what Argo
compiles in. Anything it reports that the copies do not list goes into
`has_no_conditions` in both places, and upstream. Re-run it after a
Crossplane upgrade, which is what moves the answer.

### What else the plane configures

- **`resource.exclusions`** for `ProviderConfigUsage` and
  `ClusterProviderConfigUsage`. One exists per managed resource and
  carries no meaning of its own.
- **`ARGOCD_K8S_CLIENT_QPS: 300`** on the application controller, in
  `management-argo`'s values in
  `infra/platform/crossplane-xrds/xmanagementplane-composition.yml`.
  The default is 50, and a plane serving a provider's worth of CRDs
  spends that on discovery. Reaching a running plane takes the upgrade
  procedure — see [argocd-upgrades](argocd-upgrades.md).

### Writing a check of your own

Two passes over `status.conditions`, `Synced` before `Ready`. One loop
answers whichever condition the array happened to hold first, and a
composite that went out of sync after it was once ready then reports
the stale success.

### Before registering `Application`

Give an environment's Applications a parent of their own. A child that
cannot go Healthy holds its parent's sync open once the kind has a
verdict, and a hung sync retries at the revision it began with — so
everything else under that parent is re-applied from a stale copy for
as long as the budget lasts.

### Checking they are installed

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data["resource.customizations"]' | grep -E '^\S.*:$'
```

One line per group in `compositeGroups`, plus `argoproj.io/Application`
and the two wildcards. A group that is not there is a verdict Argo is
not using, and the Applications relying on it read Healthy meanwhile.
Worth running after an Argo upgrade: `argocd-cm` belongs to the Argo
release and carries these keys from a second manager, so two things
write to one object.

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data["application.resourceTrackingMethod"] // "unset"'
```

`annotation` is what tracks a resource by
`argocd.argoproj.io/tracking-id`, which is what
[argocd](argocd.md) describes. Unset means the installed Argo's
default decides, so read that before assuming either.

## Failures

**An Application `Healthy` over a composite that never composed.** Argo
grades a resource by its API group, and a group it has no check for is
graded Healthy unconditionally rather than reported as ungraded. An
Application applying a composite that fails to compose is
indistinguishable from one that worked.

**A managed resource `Healthy` while it is still provisioning.** The
precedence bug: no status yet, so the nil branch answers Healthy before
the kind list is reached. Every managed resource on a plane running
Argo's compiled-in scripts does this, and the composite above it looks
finished the moment it was applied.

**A kind `Progressing` for good after an Argo upgrade.** The other half
of the same bug. A kind that carries no status *and* is missing from
the list is graded Healthy by accident today, so the list looks
complete while it is not — and a release correcting the precedence
grades that kind Progressing for ever, taking its Application with it.
`gcp-plane-statusless-kinds` is what finds them before that happens.

**A parent whose waves gate nothing.** The waves are doing what waves
do. `Application` is an ungraded kind — no Lua under
`resource_customizations/argoproj.io/`, and the Go switch on
`argoproj.io` handles `Workflow` alone — and gitops-engine treats a nil
health as an immediate success, the way it does a `Secret`. So every
wave succeeds the moment it is applied and the next begins, and the
parent reads Healthy throughout however its children are doing.

**A grade that never moves, on a resource Crossplane created.** Managed
resources are tree descendants rather than an Application's own, so
their grade never reaches it. The composite is the thing whose verdict
matters.

## Rules

**MUST:**

- Register a health check for every XR group a plane serves, and add a
  group to `compositeGroups` with the XRD that introduces it.
- Carry corrected copies of `*.crossplane.io/*` and `*.upbound.io/*`
  while the plane runs an Argo release without the fix, and delete them
  when it runs one with it.
- Put a wildcard group's check in `resource.customizations`. A
  ConfigMap key cannot contain a `*`.
- Re-run `just gcp-plane-statusless-kinds` after a Crossplane upgrade,
  and check `argocd-cm` still carries every entry after an Argo one.
- Read `Synced` before `Ready`, in a pass of its own.
- Register a check for `argoproj.io/Application` where a parent holds
  children in waves, and give an environment's Applications a parent of
  their own before doing so.

**MUST NOT:**

- Read `Healthy` on an ungraded group as evidence of anything.
- Patch one status-less kind rather than the script that grades it.
  There are several, in both groups.
- Expect a managed resource's grade to reach the Application above it.
- Set `application.resourceTrackingMethod` without reading what the
  plane already uses. Changing it re-tracks every resource.

## Discussion

We write a health check for every group we act on the verdict of,
because Argo's answer for a group it does not know is not "unknown" but
"Healthy" — and that answer propagates, since a wave advances on health
and gitops-engine counts a nil verdict as success.

**Why a missing check is silence rather than an error.** Argo grades by
API group and has no notion of a group it ought to know about. An
ungraded resource is not reported as ungraded: it gets the same word a
working one gets, in the same column. Nothing distinguishes the two,
which is why the list of groups belongs in the chart beside the XRDs
rather than in somebody's memory.

**The status-less trap.** The compiled-in script has to answer two
questions with one piece of Lua: a resource that will have a status and
does not have one yet is Progressing, and a resource that will never
have one is Healthy. It separates them with a list of kinds. The
precedence bug means the nil check answers first, so every status-less
kind grades Healthy whether or not it is on that list — which makes the
list look complete while it is not, and hides the omission until the
bug is fixed. Correcting the precedence without completing the list
turns a silent success into a permanent `Progressing`. Carrying our own
corrected copies is what makes this installation indifferent to which
of the two readings the installed release has, and patching a single
kind is not: the affected kinds run to four in the crossplane list
alone, and the upbound script has its own.

**Why the `Application` case costs something.** Registering it is not
turning waves on — they were always ordering, on a signal that was
always success. Giving the kind a verdict makes the signal real, and a
real signal can say no: a child that hangs now hangs its parent, and a
hung sync replays a stale revision. So the fix wants the Applications
it gates to be ones that can fail without taking a plane's own
manifests with them.

## References

- [argocd](argocd.md) — Applications, waves, and reading a sync that is
  not applying.
- [argocd-upgrades](argocd-upgrades.md) — how a values change reaches a
  running plane.
- [crossplane](crossplane.md) — the composites whose verdict this is
  about.
