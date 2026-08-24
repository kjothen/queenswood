# Upgrading and reconfiguring Argo CD

<!-- tessl-plugin: deployment -->

## Status

Not yet run. The steps are derived from the composition and the live
release rather than from having performed them, and the first person to
follow them should correct them as they go.

## Problem

You want to change Argo CD on a management plane: its chart version, a
resource request, or any other chart value.

## Solution

Every command below reads these:

```bash
export CODE=qw01        ## example, qw01
export VERSION=10.2.1   ## example, whatever the composition pins
```

### 1. Change it in git, and merge

Two files, both required:

- `infra/helm/boot-management-plane/Chart.yaml` — the `argo-cd`
  dependency version, for a version change.
- `infra/platform/crossplane-xrds/xmanagementplane-composition.yml` —
  the `management-argo` resource. Its `chart.version` for a version
  change; its `values:` block for anything else.

Then:

```bash
just check-versions
```

Merge before touching the running plane.

### 2. Read what the plane is running

```bash
kubectl --context "$CODE-mgmt" -n argocd get deploy argocd-server \
  -o jsonpath='{.metadata.labels.helm\.sh/chart}{"\n"}'
helm --kube-context "$CODE-mgmt" get values argocd -n argocd -o yaml \
  > argocd-values.yaml
```

`argocd-values.yaml` now holds every value the release was installed
with, including `extraObjects`.

### 3. Add the change to that file

Edit `argocd-values.yaml`. Add what changed and leave everything else
exactly as it is — `extraObjects` in particular.

### 4. Upgrade

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update argo
helm --kube-context "$CODE-mgmt" upgrade argocd argo/argo-cd \
  --version "$VERSION" \
  -n argocd -f argocd-values.yaml
```

### 5. Verify, in this order

```bash
# the bootstrap Application still exists
kubectl --context "$CODE-mgmt" -n argocd get application management-plane

# every component came back
kubectl --context "$CODE-mgmt" -n argocd get pods

# and Argo still reconciles
kubectl --context "$CODE-mgmt" -n argocd get applications
```

The first is the one that matters. Everything else is recoverable.

### If it goes wrong

```bash
helm --kube-context "$CODE-mgmt" history argocd -n argocd
helm --kube-context "$CODE-mgmt" rollback argocd <revision> -n argocd
```

If `management-plane` is gone, recreate it from the composition's
`values.extraObjects` before anything else: without it the plane reads
no git at all.

## Rules

**MUST:**

- Merge the change before upgrading the plane.
- Change the version in both the boot chart and the composition.
  `check-versions` fails on one without the other.
- Build the values file from `helm get values`, never from scratch.
- Pin `--version` to what the composition says.
- Confirm `management-plane` still exists before anything else.

**MUST NOT:**

- Upgrade with a values file that omits `extraObjects`.
- Set `management.bootstrap: true` to make the composition
  authoritative.
- Expect a merged change to reach a running plane on its own.

**MAY:**

- Use `--reuse-values` instead of a written-out file.
- Leave a plane on an older chart than git describes, deliberately.
  Nothing detects it.

## Discussion

**Why merging is not enough.** A boot plane installs Argo, and the
`Release` describing it carries `Observe` alone once the plane is
running: the plane reconciles the composite that describes itself and
never acts on the release that installed it. A merged change therefore
reaches the next plane a boot plane builds, and never this one — with
no error, because the `Release` reports `Synced` while doing nothing.
That is the policy working.

**Why `helm upgrade` works at all.** `Observe` is Crossplane declining
to act, not a lock. The release is an ordinary Helm release whose state
lives in `sh.helm.release.v1.argocd.*` Secrets, and any client with
cluster access can upgrade it. Crossplane observes the result
afterwards and does nothing.

**Why the values file is read from the cluster.** The composition gives
this release exactly one value: `extraObjects`, holding the
`management-plane` Application — the thing that points the plane at
git. Helm deletes what a previous manifest carried and a new one does
not. That Application has `resources-finalizer.argocd.argoproj.io` and
`prune: true`, so deleting it cascades through the child Applications
to the composites, and an instance's cluster and node pool are managed
resources that permit `Delete`. A values file that forgets one key
reaches GCP.

**Why merge first.** The manifest is the record of what a plane should
be. Upgrading first leaves a plane running something git does not
describe, and the next rebuild reverts it without anyone deciding to.

**Why not `management.bootstrap: true`.** It switches both Releases to
`Observe, Create, Update, LateInitialize`, which makes the composition
authoritative and looks like the declarative answer. It also hands the
plane's Crossplane the Helm release installing the Argo that applies
what that Crossplane reads. A bad render then leaves nothing standing
that can fix it except a fresh boot plane.

**When to rebuild instead.** A rebuild through a boot plane installs
from the composition and leaves no divergence, which is worth it when
several changes have accumulated, or when nobody is sure what the plane
is running any more. See
[crossplane-app-deployment](crossplane-app-deployment.md).

**What this does not cover.** Argo applies `infra/helm/management-plane`
from git on every sync, so anything in that chart — the
`DeploymentRuntimeConfig` objects shaping providers and functions among
them — is an ordinary merge that lands on the running plane with none
of this. Only the release Argo itself runs from is `Observe`.

## References

- [argocd](argocd.md) — how Argo applies what it does own
- [crossplane-app-deployment](crossplane-app-deployment.md) — building a
  plane, and the rebuild path
- [queenswood-installation](queenswood-installation.md) — what a plane
  cannot change about itself
