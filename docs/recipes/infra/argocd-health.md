# Argo CD health

<!-- tessl-plugin: deployment -->

## Status

**Verified**, 2026-08-27, on this installation's plane: the entries
were the ones the chart carries, and `gcp-plane-statusless-kinds`
reported nothing missing from either list.

## Problem

You want to know that Argo is grading a plane with the checks the chart
carries.

## Solution

### Prerequisites

- A management plane running in the installation's folder.
- `platformViewer`, e.g. `grp-gcp-<code>-platform-viewer@`.

```bash
# the installation code, e.g. qw01
export CODE=qw01
```

### 1. Check the entries are registered

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data["resource.customizations"]' | grep -E '^\S.*:$'
```

One line per group in `compositeGroups`, plus `argoproj.io/Application`
and the two wildcards.

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data | keys[]
    | select(startswith("resource.customizations.health."))'
```

Nothing.

### 2. Check the lists cover the plane

```bash
just gcp-plane-statusless-kinds
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
key beats a wildcard, so a stale one goes on grading its kind by
whatever an earlier generation wrote.

**A kind named under `missing from` in step 2.** It carries no status
and no list names it, so it reports Healthy today from the nil branch
and goes `Progressing` for ever once the precedence is corrected,
taking its Application with it. Add it to `has_no_conditions` in
`infra/helm/management-plane/templates/argocd-cm.yaml` and in
`scripts/crossplane-statusless-kinds.py`, and upstream.

**An Application `Healthy` over a composite that never composed.** Argo
grades a resource by its API group, and a group it has no check for is
graded Healthy unconditionally rather than reported as ungraded. An
Application applying a composite that fails to compose is
indistinguishable from one that worked.

**A managed resource `Healthy` while it is still provisioning.** A
plane running Argo's compiled-in scripts rather than the chart's copies
does this to every managed resource, and the composite above it looks
finished the moment it was applied. Step 1 is what tells the two apart.

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

- Run step 1 after an Argo upgrade and step 2 after a Crossplane one.
  An upgrade is what moves either answer.
- Add a group to `compositeGroups` with the XRD that introduces it.
- Give an environment's Applications a parent of their own before
  registering a check for `argoproj.io/Application`.
- Read `Synced` before `Ready`, in a pass of its own, in a check of
  your own.

**MUST NOT:**

- Read `Healthy` on an ungraded group as evidence of anything.
- Patch one status-less kind rather than the script that grades it.
  There are several, in both groups.
- Expect a managed resource's grade to reach the Application above it.

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

**The status-less trap.** The script Argo ships for Crossplane has to
answer two questions with one piece of Lua: a resource that will have a
status and does not have one yet is Progressing, and a resource that
will never have one is Healthy. It separates them with a list of kinds,
and its condition reads as `A or (B and C)` where `(A or B) and C` was
meant — so the nil check answers first, and every status-less kind
grades Healthy whether or not the list names it. That makes the list
look complete while it is not, and hides the omission until the bug is
fixed. Correcting the precedence without completing the list turns a
silent success into a permanent `Progressing`.

**Why the chart carries its own copies.** `argoproj/argo-cd#29382`
fixes both halves — the precedence, and the kinds the lists were
missing — and until it reaches a release this plane runs, a plane using
what Argo compiles in grades every provisioning resource Healthy. So
the chart carries corrected copies of the two scripts as
`*.crossplane.io/*` and `*.upbound.io/*`, transcribed from that PR
rather than adapted, so the diff against upstream stays readable while
somebody checks whether it has landed.

They are meant to be deleted. When a release carrying the fix is the
one this plane runs, remove both entries from
`infra/helm/management-plane/templates/argocd-cm.yaml` and point
`HAS_NO_CONDITIONS` in `scripts/crossplane-statusless-kinds.py` at the
upstream lists, so step 2 goes back to diffing against Argo's.

**Which upstream file each copy is.** They are
`resource_customizations/_.crossplane.io/_/health.lua` and its upbound
twin. A `_` path segment is how that tree spells the wildcard a
directory name cannot carry, so `_.crossplane.io/_` is the
`*.crossplane.io/*` entry here — the same wildcard in a third encoding,
since a ConfigMap key cannot express it at all. That is also why all
four entries sit in one `resource.customizations` block rather than in
four dotted keys.

**How a composite is graded.** Two passes over `status.conditions`,
`Synced` before `Ready`. One loop answers whichever condition the array
happened to hold first, and a composite that went out of sync after it
was once ready then reports the stale success.

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
- [crossplane](crossplane.md) — the composites whose verdict this is
  about.
