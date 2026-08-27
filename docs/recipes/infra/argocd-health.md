# Argo CD health

<!-- tessl-plugin: deployment -->

## Status

**Verified**, 2026-08-27, on this installation's plane: step 1 gave the
five and nothing else, and step 2 reported nothing missing.

## Problem

You need Argo CD to report the correct health status for your
installation.

## Solution

### Prerequisites

- A management plane running in the installation's folder.
- Steps 1 and 2 — the `platformViewer` capability, e.g. the Google
  group `grp-gcp-<code>-platform-viewer@`.

```bash
# the installation code, e.g. qw01
export CODE=qw01
```

### 1. Check Argo has the health checks

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data["resource.customizations"]' | grep -E '^\S.*:$'
```

Exactly these five, and nothing else:

```
platform.repldriven.com/*:
queenswood.repldriven.com/*:
argoproj.io/Application:
"*.crossplane.io/*":
"*.upbound.io/*":
```

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data | keys[]
    | select(startswith("resource.customizations.health."))'
```

Nothing.

### 2. Check every status-less Crossplane kind is handled

```bash
just gcp-plane-crossplane-statusless-kinds
```

`none` under both `missing from` headings.

## Failures

**A group in `compositeGroups` with no line in step 1.** The chart
renders one entry per group in that list, so a missing line is an XRD
loaded onto the plane whose group was never added beside it. Every
composite of that kind reads Healthy, including one that failed to
compose.

**A `resource.customizations.health.<group>_<kind>` key in step 1.**
Server-side apply removes a field when the manager that owned it stops
declaring it, so one that survives is owned by something else. An exact
key beats a wildcard, so a stale one goes on assessing its kind by
whatever an earlier generation wrote.

**A kind named under `missing from` in step 2.** It carries no status
and no list names it, so it reports Healthy today from the nil branch
and goes `Progressing` for ever once the precedence is corrected,
taking its Application with it. Add it to `has_no_conditions` in
`infra/helm/management-plane/templates/argocd-cm.yaml` and in
`scripts/crossplane-statusless-kinds.py`, and upstream.

**An Application `Healthy` over a composite that never composed.** Argo
assesses a resource by its API group, and a group it has no check for
is reported Healthy unconditionally rather than as unassessed. An
Application applying a composite that fails to compose is
indistinguishable from one that worked.

**A managed resource `Healthy` while it is still provisioning.** A
plane running Argo's compiled-in scripts rather than the chart's copies
does this to every managed resource, and the composite above it looks
finished the moment it was applied. Step 1 is what tells the two apart.

**A parent whose waves gate nothing.** The waves are doing what waves
do. `Application` has no health check of its own: Argo removed the
assessment for the kind in 1.8, there is no Lua under
`resource_customizations/argoproj.io/`, and the Go switch on
`argoproj.io` handles `Workflow` alone. gitops-engine then treats a
missing health as an immediate success, the way it does a `Secret`. So
every wave succeeds the moment it is applied and the next begins, and
the parent reads Healthy throughout however its children are doing.

**A health status that never moves, on a resource Crossplane created.**
Managed resources are tree descendants rather than an Application's
own, so their health never reaches it. The composite is the thing whose
status matters.

## Rules

**MUST:**

- Re-read `argocd-cm` after an Argo upgrade, and re-run
  `just gcp-plane-crossplane-statusless-kinds` after a Crossplane one.
  An upgrade is what moves either answer.
- Add an XRD's API group to `compositeGroups` in
  `infra/helm/management-plane/values.yaml`, in the same change as the
  XRD, where the group is not there already. One entry covers every
  kind in a group.
- Give an environment's Applications a parent of their own before
  registering a check for `argoproj.io/Application`.
- Read `Synced` before `Ready`, in a pass of its own, when writing a
  check.

**MUST NOT:**

- Read `Healthy` on a group with no check as evidence of anything.
- Patch one status-less kind rather than the script that checks it.
  There are several, in both groups.
- Expect a managed resource's health to reach the Application above it.

**SHOULD:**

- Delete the `*.crossplane.io/*` and `*.upbound.io/*` entries from
  `infra/helm/management-plane/templates/argocd-cm.yaml`, and point
  `HAS_NO_CONDITIONS` in `scripts/crossplane-statusless-kinds.py` at
  Argo's own lists, once a release carrying `argoproj/argo-cd#29382`
  is the one the plane runs. Keeping them is defensible only for a
  kind upstream still does not list.

## Discussion

We write a health check for every group whose health status we act on,
because Argo's answer for a group it does not know is not "unknown" but
"Healthy" — and that answer propagates, since a wave advances on health
and gitops-engine counts a missing status as success.

**Why a missing check is silence rather than an error.** Argo assesses
by API group and has no notion of a group it ought to know about. A
resource with no check is not reported as unassessed: it gets the same
word a working one gets, in the same column. Nothing distinguishes the
two, which is why the list of groups belongs in the chart beside the
XRDs rather than in somebody's memory.

**The status-less trap.** The script Argo ships for Crossplane has to
answer two questions with one piece of Lua: a resource that will have a
status and does not have one yet is Progressing, and a resource that
will never have one is Healthy. It separates them with a list of kinds,
and its condition reads as `A or (B and C)` where `(A or B) and C` was
meant — so the nil check answers first, and every status-less kind
reports Healthy whether or not the list names it. That makes the list
look complete while it is not, and hides the omission until the bug is
fixed: correcting the precedence without completing the list turns a
silent success into a permanent `Progressing`. Which is why
`argoproj/argo-cd#29382` fixes both halves at once.

**The copies, and what deletes them.** Until that reaches a release
this plane runs, the chart carries corrected copies of the two scripts,
as `*.crossplane.io/*` and `*.upbound.io/*`, transcribed from the PR
rather than adapted so the diff against upstream stays readable while
somebody checks whether it has landed. Upstream they are
`resource_customizations/_.crossplane.io/_/health.lua` and its upbound
twin: a `_` path segment is how that tree spells the wildcard a
directory name cannot carry, so `_.crossplane.io/_` is the
`*.crossplane.io/*` entry here. A ConfigMap key cannot express that
wildcard at all, which is why every entry sits in one
`resource.customizations` block rather than in five dotted keys.

They are meant to be deleted. When a release carrying the fix is the
one this plane runs, remove both entries from
`infra/helm/management-plane/templates/argocd-cm.yaml` and point
`HAS_NO_CONDITIONS` in `scripts/crossplane-statusless-kinds.py` at the
upstream lists, so step 2 diffs against Argo's again.

**What step 2 does not read.** Managed resources. upjet gives every one
of them a status, so none can be status-less, and their CRDs are large
enough that fetching them all costs hundreds of megabytes — so the
`upbound.io` half fetches the config-shaped kinds and leaves the rest.
That is a heuristic rather than a proof: a provider shipping a
status-less kind whose name is neither a provider config nor a store
config would not be read. The `crossplane.io` half has no such filter
and reads every group.

**How a composite is checked.** Two passes over `status.conditions`,
`Synced` before `Ready`. One loop answers whichever condition the array
happened to hold first, and a composite that went out of sync after it
was once ready then reports the stale success.

**Why the `Application` case costs something.** Argo removed the health
assessment for the kind in 1.8 and documents restoring it for exactly
this case — an app-of-apps ordering its children by sync wave — so the
entry in the chart is a documented restoration rather than something
prised out of the source.

Registering it is not turning waves on — they were always ordering, on
a signal that was always success. Giving the kind a health status makes
the signal real, and a real signal can say no: a child that hangs now
hangs its parent, and a hung sync replays a stale revision. So the fix
wants the Applications it gates to be ones that can fail without taking
a plane's own manifests with them.

## References

- [argocd](argocd.md) — Applications, waves, and reading a sync that is
  not applying.
- [crossplane](crossplane.md) — the composites whose health this is
  about.
