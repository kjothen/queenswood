# Argo CD

<!-- tessl-plugin: deployment -->

## Problem

An Application that cannot apply one resource stops applying the rest,
and the resource it cannot apply is often the one whose installer it
was about to run.

## Solution

Keep concrete resources out of parents, and know which failures retry
and which wait for a human.

### App of apps

A parent holds Applications and resources of kinds that always exist.
Anything of a kind installed by one of its children belongs in a child
of its own.

Otherwise: the parent fails building a sync task for an unknown kind,
never applies the child that would install it, and the fix cannot
reach the cluster. Breaking that needs a hand-applied patch, and
hand-applying needs write access an operator should not hold.

### Waves and missing kinds

Waves order applies. They do not make a kind exist. A resource whose
CRD arrives in an earlier wave still fails validation without
`argocd.argoproj.io/sync-options: SkipDryRunOnMissingResource=true`,
and that skips the dry run only — the apply still fails until the CRD
is registered.

An operator install is asynchronous. Argo reports a `Provider` or a
chart applied long before its CRDs exist, so the retry budget must
outlast an image pull.

### Server-side apply

CRDs over 256KB fail client-side apply: the whole resource is written
into `last-applied-configuration`, which the API server rejects as
`Too long`. The symptom is a crash-looping workload complaining that
its own kinds do not exist, not a failed CRD. Set
`ServerSideApply=true`.

### A rendered chart holds no generated value

Argo renders with `helm template`, where `lookup` returns nothing. A
chart that mints a value and preserves it by looking up the live object
therefore mints a fresh one on every render, and the sync applies it —
so the "generate once" branch is the only branch that ever runs.

The damage is quiet where the value has a counterpart somewhere else.
A self-signed keypair whose public half was registered on another
system keeps verifying against the half that was registered, while the
pods sign with the half that was rendered last, and the two only ever
meet at a failed authentication. Nothing reports a drifted secret,
because from Argo's side the Secret is exactly what git says.

Generate such a value in the cluster instead, from a Job that reads
what is stored before it makes anything, and let the chart declare the
Secret with no `data` at all. Server-side apply then leaves the
contents to whichever manager wrote them: Argo never declares that
field, so it never owns it and never clears it. Where the value has a
counterpart, generating and registering it belong to the same Job, or
they drift apart again for a different reason.

Rendering key material has a second cost even where nothing drifts —
the private half exists in the repo-server, in whatever the render is
cached in, and in the Application's live manifest view. See
[external-secrets](external-secrets.md) for the values that come from
outside and belong in Secret Manager, and for why a value you can
regenerate belongs in neither place.

### Retries

`selfHeal` corrects drift. It does not retry a failed sync — those are
different things, and an Application whose sync failed stays failed
whatever `selfHeal` says. One that exhausts its retry budget then
stops until the revision changes or someone syncs it. A fix merged
after the budget ran out still needs a nudge, and a nudge is cluster
write access. Prefer budgets that outlast an ordinary incident.

Trigger a sync without the CLI by setting `.operation` on the
Application, and cancel one by removing it.

Cancelling is the part worth knowing, because a **running** retry loop
is worse than an exhausted one. An operation pins the revision it
started with, and each retry replays that revision's manifests — so a
fix merged while it is retrying never arrives, however many attempts
remain. With a budget backing off to ten minutes, that is an hour of
replaying a fault that no longer exists in git.

The status reads as though the fix has landed.
`status.sync.revisions` shows the revision Argo *would* sync, which
updates the moment the merge is polled;
`.operation.sync.revisions` shows the one being retried, and only that
second one says what is actually being applied. Compare them before
concluding anything, and remove `.operation` to let a fresh sync start
at the current revision.

### An app is stuck applying a previous version

`status.sync.revision` is the revision Argo would sync;
`status.operationState.syncResult.revision` is the one the running
operation is applying. Where they differ and the phase is `Running`,
every merge since the second one is waiting behind an operation that
started before them.

A retry loop is one cause, and the section above covers it. The other
reads identically and is worse: an operation that is not failing at all.
A wave waits for its resources to report Healthy, and only `Healthy` and
`Degraded` end a task — `Progressing`, `Suspended`, `Missing` and
`Unknown` all leave it running. So a wave holding a resource that can
never become Healthy waits for good. No retry, no backoff, no timeout,
and `retryCount` stays unset, which is how to tell the two apart.

Cancelling takes one of two acts, and they are not interchangeable.

Removing `.operation` stops one that is *queued* — a retry waiting its
turn, or a sync nobody has started. It takes a JSON patch, because a
merge patch cannot remove a field:

```
kubectl -n argocd patch app <app> --type json \
  -p '[{"op":"remove","path":"/operation"}]'
```

That does nothing to one already in flight. An operation parked in a
health wait is not waiting to be re-queued, it is waiting for a
resource, and the controller goes on holding it: the field disappears
and `operationState.phase` stays `Running` at its original
`startedAt`. Terminating is what reaches it, and what `argocd app
terminate-op` does:

```
kubectl -n argocd patch app <app> --type merge \
  -p '{"status":{"operationState":{"phase":"Terminating"}}}'
```

A plain patch reaches `status` because the Application CRD declares no
status subresource. Check that before relying on it.

Merge the fix before either. Both leave an Application whose automated
sync starts again at the current revision, so cancelling first buys one
more hang.

### One bad object fails the whole sync

A sync is one operation over every resource, so a single object the
API server rejects leaves every well-formed one beside it reported
`OutOfSync` — and the message names the fault, not the resources
waiting behind it. The cluster looks mostly right while the
Application keeps failing.

Server-side apply is stricter than a rendered manifest looks. An
undeclared field is refused outright rather than dropped, so
`.spec.foo: field not declared in schema` fails the object entirely.
`helm template` renders it happily; only the API server has the
schema. A template that renders is not a template that applies.

A `valuesObject` is YAML, so a bare `n`, `y`, `no` or `yes` in one is a
boolean rather than a short string. A chart building a name with
`printf "%s"` then renders `%!s(bool=false)`, and what fails is the API
server refusing a resource name containing a `%` — which reads as a
templating fault rather than as a missing pair of quotes two files
away. Quote every short value, and have the chart fail on a non-string
rather than compose one: coercing is worse, because `false` is a name
that applies cleanly and is wrong.

### A group with no health check reads Healthy

Argo grades a resource by its API group. A group it has no check for
is reported Healthy whatever the resource is doing, so an Application
applying a composite that fails to compose is indistinguishable from
one that worked. Register a check for every XR group a plane serves.

Only what an Application manages reaches its health. Crossplane creates
managed resources rather than Argo, so they are tree descendants and
never feed the Application above them — the composite is the thing
whose grade matters, and the one upstream covers least.

Argo's compiled-in `_.upbound.io` and `_.crossplane.io` scripts are
snippets vendored from Crossplane's documentation, and both carry a
precedence bug: they read as `A or (B and C)` where `(A or B) and C`
was meant, so a resource whose status has not been written yet reports
Healthy while the same resource with an empty status reports
Progressing. Mostly that grades the tree and nothing else, which is
worth knowing when reading the tree and not worth vendoring a corrected
copy to fix.

Check what the bug is holding up before correcting it. A kind that
carries no status ever and is missing from the script's list of
status-less kinds grades Healthy only because the nil branch answers
before the list is consulted — `EnvironmentConfig` is one, and a
corrected script would leave it Progressing for good, taking its
Application with it. State such a kind's health explicitly, by exact
`<group>_<kind>` key, and the answer stops depending on which reading
the installed release has.

The list is derivable rather than remembered: a kind can never carry a
status when no served version of its CRD declares a `status`
subresource and none declares a `status` property.
`just gcp-plane-statusless-kinds` reads that off a plane and diffs it
against what Argo compiles in. It is worth re-running on a Crossplane
upgrade, which is what moves the answer.

Read `Synced` before `Ready` in a check of your own, in a pass of its
own. One loop answers whichever condition the array happened to hold
first, and a composite that went out of sync after it was once ready
then reports the stale success.

### An app-of-apps only appears to gate

`Application` is one of those groups. Argo ships no health check for
the kind — no Lua under `resource_customizations/argoproj.io/`, and the
Go switch on `argoproj.io` handles `Workflow` alone — so a child
Application has no health, and a parent reads Healthy however its
children are doing.

That reaches further than reporting. A wave waits for its resources to
go Healthy before the next one starts, and gitops-engine treats a nil
health as an immediate success, the way it does a `Secret`. So a parent
holding Applications in waves does not order them at all: every wave
succeeds the moment it is applied, and the ordering the waves were
written for never happens.

Registering a check for the kind restores both, and the second one has
a cost. A child that cannot become Healthy now holds its parent's sync
open, and a hung sync retries at the revision it began with — so
everything else in that parent is re-applied from a stale copy for as
long as the budget lasts. Before turning it on, make sure no parent
holds both an environment's Applications and anything that has to keep
reconciling while that environment is off.

### A resource keeps the tracking annotation of its last owner

Argo records which Application owns a resource on the resource itself,
as `argocd.argoproj.io/tracking-id`. Nothing else removes it. So a
resource that moves between Applications — committed in one and
composed by something else afterwards, or moved between directories —
arrives at its new owner still carrying the old one's name.

The former owner then goes on listing it. Where that owner prunes, the
resource is deleted, which is worse. Where it does not, the resource
sits in the tree as `OutOfSync` for good, the Application never reports
Synced again, and its health takes the worst of a resource it no longer
manages — up through every parent above it.

Strip it once, by hand, as the last step of the handover:

```
kubectl -n argocd annotate <kind> <name> argocd.argoproj.io/tracking-id-
```

A controller that took the resource over will not do it for you. It
writes the fields its manifest declares, and an annotation it never
mentions is one it never touches.

### Revisions

Argo reads the revision an Application names, not a working tree. A
change is not testable until it is merged, unless the revision is a
field the manifest can set.

## Rules

**MUST:**

- Keep concrete resources out of a parent Application. Put anything
  whose kind a child installs into a child of its own.
- Set `ServerSideApply=true` for charts with large CRDs.
- Set retry budgets that outlast an operator install.
- Register a health check for `argoproj.io/Application` if a parent is
  meant to order its children by wave. Without one the kind has no
  health, and every wave succeeds on apply.
- Give an environment's Applications a parent of their own before doing
  so, or a parent gates the plane's own manifests on that environment's
  workloads.
- Register a health check for every XR group a plane serves. A group
  with no check reports Healthy however its composites are doing.
- Read `.operation.sync.revisions` rather than `status.sync.revisions`
  when a sync is failing. The first is what is being retried; the
  second is only what would be synced next.
- Remove `.operation` to cancel a retry loop replaying a revision whose
  fault is already fixed. Merging does not reach it. Use a JSON patch —
  a merge patch cannot remove a field — and merge the fix before
  cancelling, or the fresh sync hangs the same way.
- Read `retryCount` before calling a stuck sync a retry loop. Unset
  means the operation is not failing at all: only `Healthy` and
  `Degraded` end a task, so a wave holding anything else waits with no
  retry, no backoff and no timeout.
- Terminate an operation already in flight, rather than removing
  `.operation`, which only stops a queued one. Set
  `status.operationState.phase` to `Terminating`.
- Strip `argocd.argoproj.io/tracking-id` from a resource handed from one
  Application to another. Nothing else removes it, and the former owner
  holds it `OutOfSync` for good.
- Set `prune: false` where pruning would delete something a missing
  file should not delete.
- Merge a change before expecting Argo to apply it. It reads the
  revision an Application names, never a working tree.
- Generate a value in the cluster, not in a chart Argo renders, and
  let the chart declare the Secret without `data` so server-side apply
  leaves the contents to the Job that writes them.

**MUST NOT:**

- Expect sync waves to resolve a missing kind.
- Expect `SkipDryRunOnMissingResource` to make an apply succeed.
- Expect a merged fix to reach an Application whose retries have
  already been exhausted.
- Rely on `lookup` to preserve anything. Argo renders with `helm
  template`, where it returns nothing and the generate branch always
  wins.

## References

- [crossplane](crossplane.md) — what Argo is usually delivering here.
