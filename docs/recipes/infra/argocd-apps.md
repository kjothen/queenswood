# Argo CD Applications

<!-- tessl-plugin: deployment -->

## Status

**Verified**, 2026-08-27, on this installation's plane: a merged change
showed as a new `REV` with `PHASE` `Succeeded` and a `FINISHED` later
than the merge, about a minute after it landed. Both checks under
Failures were run, and clearing a stale `operationState` held across
five minutes of reconciles.

## Problem

You need Argo CD to apply a change you have merged — to a chart, an
XRD, an installation's manifest.

## Solution

### Prerequisites

- A management plane running in the installation's folder.
- Google group memberships, by capability:
  - `platformViewer`, e.g. `grp-gcp-<code>-platform-viewer@`.

The `just` recipe reads the installation code from the justfile. The
`kubectl` commands under Failures take it from the shell:

```bash
# the installation code, e.g. qw01
export CODE=qw01
```

### Check a merged change landed

Merge it first. Then:

```bash
just gcp-argo-apps-status
```

- `SYNC` `Synced` on every row.
- `HEALTH` `Healthy` on every row, eventually. `Progressing` on an
  instance's Applications while its workloads converge is not a
  finding.
- `REV` the commit you merged, on whatever reads it. An Application
  whose source is a Helm repository shows a chart version instead.
- `PHASE` `Succeeded` wherever there is an operation at all. `-` in
  `PHASE`, `REV` and `FINISHED` together is an Application that has not
  synced since the controller restarted or since somebody cleared the
  record: unremarkable on a row your change does not touch, and the
  finding itself on one that should have applied it.
- `FINISHED` a time later than your merge, on the rows that carry your
  change.

## Failures

**A child that never applies, holding the kind the parent needed.** The
parent Application fails building a sync task for a kind the API server
does not serve, and stops before applying the child that would install
it. What it holds:

```bash
kubectl --context "$CODE-mgmt" -n argocd get application management-plane \
  -o json | jq -r '[.status.resources[].kind] | unique | .[]'
```

Exactly these six, and nothing else:

```
Application
ConfigMap
DeploymentRuntimeConfig
Role
RoleBinding
ServiceAccount
```

Anything else is a resource to move into a child of its own. The fix
cannot reach the cluster on its own: breaking the cycle takes a
hand-applied patch, and hand-applying takes cluster write access an
operator should not hold.

**A workload crash-looping because its own kinds do not exist.** Not a
failed CRD — a CRD over 256KB, whose whole body client-side apply
writes into `last-applied-configuration`, which the API server rejects
as `Too long`. The Application reports the chart applied. Set
`ServerSideApply=true`.

**A merged fix that never arrives, on an Application that is
retrying.** An operation pins the revision it started with and each
retry replays that revision's manifests, so a fix merged mid-loop is
never applied however many attempts remain — an hour of it, with a
budget backing off to ten minutes. The status reads as though the fix
landed: `status.sync.revisions` shows the revision Argo _would_ sync
and updates the moment the merge is polled, where
`.operation.sync.revisions` shows the one being retried. Compare the
two, then merge the fix and remove `.operation`, which takes a JSON
patch because a merge patch cannot remove a field:

```
kubectl -n argocd patch app <app> --type json \
  -p '[{"op":"remove","path":"/operation"}]'
```

**An operation that never finished, on an Application reporting `Synced`
and `Healthy`.** Those two describe the comparison between git and the
cluster, not the operation, so the first three columns stay green while
a sync sits open — `PHASE` and `FINISHED` are what show it, as a phase
that is not `Succeeded` and no finish time at all. With `retryCount` unset it
is not failing at all: only `Healthy` and `Degraded` end a sync task —
`Progressing`, `Suspended`, `Missing` and `Unknown` all leave it running
— so a wave holding a resource that can never go Healthy waits for good,
with no retry, no backoff and no timeout. Removing `.operation` does
nothing to one already in flight: the field disappears and
`operationState.phase` stays `Running` at its original `startedAt`.
Terminating is what reaches it, and is what `argocd app terminate-op`
does — but only while `.operation` is still there to drive the
processing:

```
kubectl -n argocd patch app <app> --type merge \
  -p '{"status":{"operationState":{"phase":"Terminating"}}}'
```

**A `PHASE` of `Terminating` that never ends.** Operation processing is
driven by `.operation`, so removing it strands whatever `operationState`
was left behind: the controller goes on reconciling the Application —
`setop_ms=0` on every pass — and never looks at that phase again.
Nothing is running, and the row is a record of something that stopped
being processed rather than something in progress. Remove the stale
state, which a plain patch reaches because the Application CRD declares
no status subresource:

```
kubectl -n argocd patch app <app> --type json \
  -p '[{"op":"remove","path":"/status/operationState"}]'
```

**An Application that stays failed after the drift was corrected.**
`selfHeal` corrects drift and does not retry a failed sync. One that
has exhausted its retry budget stops until the revision changes or
somebody syncs it, so a fix merged after the budget ran out needs a
nudge — and a nudge is cluster write access. What each Application's
sync policy carries:

```bash
just gcp-argo-apps-sync-policy
```

- `RETRY` at least 5 on every row. `-` is an Application that does not
  retry at all.
- `PRUNE` false on `installation`, and on any unit that has not turned
  it on.
- `SSA` — server-side apply, `ServerSideApply=true` in `syncOptions` —
  true on `external-secrets`, and on any Application installing a chart
  whose CRDs exceed 256KB.

**Every resource `OutOfSync`, and the message naming one of them.** A
sync is one operation over every resource, so a single object the API
server rejects leaves every well-formed one beside it unapplied. The
cluster looks mostly right while the Application keeps failing.

**`field not declared in schema`, on a template that renders.**
Server-side apply refuses an undeclared field rather than dropping it,
and only the API server holds the schema. `helm template` renders it
happily. A template that renders is not a template that applies.

**A resource name containing `%!s(bool=false)`.** A `valuesObject` is
YAML, so a bare `n`, `y`, `no` or `yes` in one is a boolean rather than
a short string, and a chart building a name with `printf "%s"` renders
that. What fails is the API server refusing a name containing a `%`,
which reads as a templating fault rather than as a missing pair of
quotes two files away. Quote every short value, and have the chart fail
on a non-string rather than coerce one — `false` is a name that applies
cleanly and is wrong.

**A resource `OutOfSync` in an Application that no longer manages it.**
Under annotation tracking, Argo records the owning Application on the
resource itself as `argocd.argoproj.io/tracking-id`, and nothing else
removes it. A resource that moves between Applications arrives at its
new owner still carrying the old one's name, and the former owner goes
on listing it — holding it `OutOfSync` for good, and taking the worst of
a resource it no longer manages up through every parent above. Where
that owner prunes, it deletes it instead. Strip the annotation by hand,
as the last act of the handover:

```
kubectl -n argocd annotate <kind> <name> argocd.argoproj.io/tracking-id-
```

That annotation is the marker of annotation tracking. Which method a
plane uses:

```bash
kubectl --context "$CODE-mgmt" -n argocd get configmap argocd-cm \
  -o json | jq -r '.data["application.resourceTrackingMethod"] // "unset"'
```

Unset means the installed Argo's default decides. Changing it re-tracks
every resource, so read it before setting it.

**A Secret whose contents change on every sync.** A chart that mints a
value and keeps it by reading the live object back with Helm's
`lookup` mints a fresh one each render instead — see
[external-secrets](external-secrets.md).

## Rules

**MUST:**

- Keep concrete resources out of a parent Application. Put anything
  whose kind a child installs into a child of its own.
- Set `ServerSideApply=true` for charts with large CRDs.
- Set retry budgets that outlast an operator install.
- Read `.operation.sync.revisions` rather than `status.sync.revisions`
  when a sync is failing. The first is what is being retried; the
  second is only what would be synced next.
- Read `retryCount` before calling a stuck sync a retry loop. Unset
  means the operation is not failing at all.
- Merge the fix before cancelling anything, or the fresh sync hangs the
  same way.
- Remove `.operation` to cancel a queued sync, with a JSON patch, and
  terminate one already in flight by setting
  `status.operationState.phase` to `Terminating`.
- Terminate before removing `.operation`, never after. Operation
  processing is driven by that field, so removing it first leaves
  nothing to act on the phase and the state sits `Terminating` for
  good.
- Strip `argocd.argoproj.io/tracking-id` from a resource handed from
  one Application to another.
- Set `prune: false` where pruning would delete something a missing
  file should not delete.
- Merge a change before expecting Argo to apply it. It reads the
  revision an Application names, never a working tree.

**MUST NOT:**

- Expect sync waves to resolve a missing kind.
- Expect `SkipDryRunOnMissingResource` to make an apply succeed. It
  skips the dry run and nothing else.
- Expect a merged fix to reach an Application whose retries have
  already been exhausted.
- Rely on Helm's `lookup` to keep a value a chart generated once. Argo
  renders with `helm template`, where it returns nothing, so the branch
  that mints a fresh one wins on every sync.

## Discussion

We change this plane by merging, and that is the whole act. Argo
reconciles files in git onto a cluster: an Application names where they
are — a repository, a revision, a path — and where they go, then
renders what it finds, applies it, and compares the result against what
is live. Nothing else about a change reaches it, so a merge is simply a
new revision at a path an Application already reads.

The files at that path may themselves be Applications, which is how one
root reaches everything without naming any of it — the root names a
path, and what sits there decides what exists. Order across them comes
from sync waves, each waiting on the health of the one before it, which
is why what `Healthy` means is a subject of its own — see
[argocd-health](argocd-health.md).

**The plane's own tree.** `management-plane` is the parent Application,
planted by the boot chart and holding nothing but Applications:
providers in wave 1, the plane's own configuration in 2, the XRDs in 3,
and `installation` — the composite describing this installation, read
from the private repository — in 4, beside `external-secrets`. An
instance's Applications appear next to them rather than nested under
them, named for the instance.

**Why a parent cannot hold a kind its child installs.** A sync is
planned before it is applied: Argo builds a task per resource, and a
kind the API server does not serve has no task to build. The plan
fails, so nothing in that Application applies — including the child
whose chart registers the kind. Nothing about that is recoverable
through git, which is the part worth internalising: the repository can
be correct and the cluster still unable to reach it.

**What an operation pins.** A sync is an operation with a revision
attached, and retries belong to the operation rather than to the
Application. So the revision is fixed at the moment the first attempt
started, and every subsequent attempt applies those manifests — a merge
during the loop changes what Argo _would_ sync without changing what it
is syncing. That is why the two revision fields disagree, and why the
one everybody reads first is the one that does not matter.

**Why cancelling takes two different acts.** `.operation` is the field
that asks for a sync — setting it starts one without the CLI, and
removing it un-queues something that has not started, or is between
retries. An operation parked in a health wait has started and is not
waiting to be re-queued: it is waiting for a resource, so the
controller goes on holding it after the field is gone. Terminating sets
a phase the controller reads. A plain patch reaches `status` because
the Application CRD declares no status subresource; check that before
relying on it.

**Waves and the kinds they cannot conjure.** A wave orders applies. It
does not make a kind exist, and an operator install is asynchronous:
Argo reports a chart applied long before its CRDs are registered, so
the next wave can begin against an API server that does not yet serve
what it needs. `SkipDryRunOnMissingResource` skips validation, not the
apply. The only thing that reliably absorbs the gap is a retry budget
long enough to cover an image pull.

## References

- [argocd-health](argocd-health.md) — what `Healthy` means, and what a
  parent's waves do not gate without it.
- [argocd-github](argocd-github.md) — reading a private repository.
- [external-secrets](external-secrets.md) — where a value belongs when
  a chart must not hold it.
- [crossplane](crossplane.md) — what Argo is usually delivering here.
