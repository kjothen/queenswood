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

An Application that exhausts its retry budget stops until the revision
changes or someone syncs it. A fix merged after the budget ran out
still needs a nudge, and a nudge is cluster write access. Prefer
budgets that outlast an ordinary incident.

Trigger a sync without the CLI by setting `.operation` on the
Application.

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
- Set `prune: false` where pruning would delete something a missing
  file should not delete.

**MUST NOT:**

- Expect sync waves to resolve a missing kind.
- Expect `SkipDryRunOnMissingResource` to make an apply succeed.
- Expect a merged fix to reach an Application whose retries have
  already been exhausted.

## References

- [crossplane](crossplane.md) — what Argo is usually delivering here.
