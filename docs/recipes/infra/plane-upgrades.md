# Upgrading a plane's own Crossplane and Argo

<!-- tessl-plugin: deployment -->

## Status

Neither path below has been run. Both are derived from the composition,
the boot chart and the live objects rather than from having done it, and
the first person to follow one should correct it as they go.

## Problem

A management plane runs two things it did not install. A boot plane put
Crossplane and Argo there, and once the plane is running their
`Release` managed resources carry `Observe` alone — the plane
reconciles the composite that describes itself, and never acts on the
releases that installed it.

So a merged change to either reaches the next plane and never this one.
Nothing errors and nothing warns: the `Release` reports `Synced`,
because observing is the whole of what it promised to do.

That is the same obstacle whether the change is a chart version or a
resource limit, which is why they are one procedure rather than two. A
limit is not a special case to be worked around; it is an ordinary
upgrade of a release nothing upgrades.

## Solution

### What is declared where

Three files, and they are not interchangeable:

- **`infra/helm/boot-management-plane/Chart.yaml`** — the versions a
  boot plane installs, as chart dependencies. This is what Renovate
  bumps.
- **`infra/platform/crossplane-xrds/xmanagementplane-composition.yml`**
  — the `Release` for each, carrying the same version and any values.
  This is what a plane built from the composite gets, and the only
  declarative home for a value.
- **`scripts/check-versions.sh`** — holds the two equal, so bumping one
  and not the other fails a check rather than installing two different
  Crossplanes a fortnight apart.

`infra/helm/management-plane` is a different tier and not part of this.
Argo applies it from git on every sync, so what it carries — the
`DeploymentRuntimeConfig` objects shaping providers and functions among
them — is an ordinary merge that lands on the running plane. If what
you want to change lives there, none of this applies.

### Read what is running before changing it

The live release is a normal Helm release whose state is in Secrets:

```bash
helm list -n argocd
helm get values argocd -n argocd -o yaml
helm list -n crossplane-system
```

Compare the version against the composition's. They diverge silently
the moment anybody upgrades in place, and nothing reports it: the
`Release` is observing, and observing a newer chart is not an error.

### Merge before upgrading, always

The manifest is the record of what a plane should be. Upgrading first
leaves a plane running something git does not describe, and the next
rebuild reverts it without anybody deciding to — so merge the version
or the value, then bring the running plane forward to match.

### Path A — in place, with `helm upgrade`

`Observe` is Crossplane declining to act, not a lock. Any Helm client
with access to the cluster may upgrade the release, and Crossplane will
observe the result and do nothing.

**Start from the live values, never from the ones you want to add.**
The composition supplies argo-cd exactly one value: `extraObjects`,
holding the `management-plane` Application. Helm deletes what a
previous manifest carried and a new one does not, so an upgrade whose
values file omits it deletes that Application — which carries
`resources-finalizer.argocd.argoproj.io` and `prune: true`, so its
deletion cascades through the child Applications to the composites, and
an instance's cluster and node pool are managed resources that permit
`Delete`. A values file that forgets one key reaches GCP.

```bash
helm get values argocd -n argocd -o yaml > argocd-values.yaml
# edit: add what changed, keep everything already there
helm upgrade argocd argo/argo-cd --version <the composition's version> \
  -n argocd -f argocd-values.yaml
```

Pin `--version` to what the composition says, or the upgrade quietly
does two things at once. `--reuse-values` merges rather than replacing
and avoids the deletion hazard, but a written-out file is reviewable,
which is worth more on a command with this reach.

Crossplane is the same shape with a different risk: the pod being
replaced is the one reconciling everything, so managed resources stop
being reconciled for the length of the restart. They are not touched —
nothing deletes, nothing drifts — but a composite mid-reconcile picks
up where it left off rather than where it was. Do it when nothing is
syncing.

### Path B — rebuild the plane

The declarative path, and the only one that leaves no divergence: a
boot plane installs the new versions from the composition, the
composite is pivoted onto the rebuilt plane, and the boot plane is
discarded. That is [crossplane-app-deployment](crossplane-app-deployment.md),
and it is a larger act than an upgrade — but it is the act the versions
in git actually describe.

Worth it when the change is large, when several have accumulated, or
when an in-place upgrade has already been done once and nobody is sure
what the plane is running any more.

### Which to reach for

In place, for a version bump or a value change on a plane that is
serving. A rebuild, when the plane is being rebuilt anyway, or when the
upgrade is one you would not want to attempt on a live control plane —
a Crossplane major, or anything changing how packages are stored.

Neither is a way to change what a plane is: `machineType` and the rest
belong to the composite, and
[queenswood-installation](queenswood-installation.md) covers what a
plane cannot change about itself.

## Rules

**MUST:**

- Merge the change before upgrading a running plane, so the manifest
  describes what is running rather than what used to be.
- Bump the version in both `boot-management-plane/Chart.yaml` and the
  composition. `check-versions.sh` fails on one without the other.
- Start an in-place upgrade from `helm get values`, and pin `--version`
  to the composition's.
- Read the live chart version back afterwards, since nothing else
  reports the divergence.

**MUST NOT:**

- Upgrade argo-cd with a values file that omits `extraObjects`. It
  deletes the bootstrap Application, whose finalizer prunes the
  composites beneath it.
- Set `management.bootstrap: true` on a running plane to make the
  composition authoritative. It hands the plane's Crossplane the
  Helm releases installing that Crossplane and the Argo applying it,
  which is what `Observe` exists to prevent — and a bad render leaves
  nothing standing that can fix it but a fresh boot plane.
- Expect a merged change to either release to reach a running plane.
  The `Release` reports `Synced` while doing nothing, which is the
  policy working rather than failing.

**MAY:**

- Use `--reuse-values` rather than a written-out file, where the change
  is small and the deletion hazard is what you are guarding against.
- Leave a plane on an older chart than git describes, so long as the
  divergence is deliberate and written down. Nothing detects it.

## References

- [crossplane-app-deployment](crossplane-app-deployment.md) — building
  a plane, and the pivot a rebuild goes through
- [queenswood-installation](queenswood-installation.md) — the manifest,
  and what a plane cannot change about itself
- [argocd](argocd.md) — how Argo applies what it does own
- [ADR-0024](../../adr/0024-instances-are-their-own-composites.md) — the
  line between what a composite builds and what Argo installs
