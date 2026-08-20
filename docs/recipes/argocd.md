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
- Read `.operation.sync.revisions` rather than `status.sync.revisions`
  when a sync is failing. The first is what is being retried; the
  second is only what would be synced next.
- Remove `.operation` to cancel a retry loop replaying a revision whose
  fault is already fixed. Merging does not reach it.
- Set `prune: false` where pruning would delete something a missing
  file should not delete.
- Merge a change before expecting Argo to apply it. It reads the
  revision an Application names, never a working tree.

**MUST NOT:**

- Expect sync waves to resolve a missing kind.
- Expect `SkipDryRunOnMissingResource` to make an apply succeed.
- Expect a merged fix to reach an Application whose retries have
  already been exhausted.

## References

- [crossplane](crossplane.md) — what Argo is usually delivering here.
