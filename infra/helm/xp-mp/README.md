# xp-mp

Umbrella Helm chart for the Crossplane management plane. Bundles
Crossplane core, ArgoCD, and the Crossplane Provider/Function
packages the plane uses, so a single `helm template` render exposes
every image to the
[`audit-helm-chart-image-bases`](../../../.claude/skills/tessl__audit-helm-chart-image-bases/SKILL.md)
skill.

## Scope

This chart covers the layer that exists *before* Argo can
reconcile from git:

- Crossplane core (Helm dependency).
- ArgoCD core (Helm dependency — replacing the previous
  `kubectl apply -f .../install.yaml` path in
  `justfiles/deploy.just`).
- Crossplane Provider and Function packages, re-exported from
  `infra/platform/crossplane-providers/providers.yml` as
  templates.

The Argo Applications, XRDs, ProviderConfigs, and GCP managed
resources in `infra/bootstrap/` and `infra/platform/` continue to
be managed via GitOps after Argo is up. Those files are the
source of truth for reconciliation; this chart's
`templates/crossplane-providers.yaml` is the audit projection.
Keep package versions in sync between the two; the chart drives
the audit, the Argo-managed YAML drives the cluster.

## Audit usage

```bash
helm dependency update infra/helm/xp-mp
helm template xp-mp infra/helm/xp-mp > /tmp/xp-mp-rendered.yaml
# Feed /tmp/xp-mp-rendered.yaml into the audit skill (see SKILL.md).
```

The audit's image-extraction regex currently matches `image:` keys
only — Provider/Function `package:` keys need a regex extension to
appear in the audit. That follow-up is tracked against the skill,
not this chart.

## Install usage (optional)

The chart will also install end-to-end as a replacement for the
hand-rolled steps in `just kind-xp-up`:

```bash
helm dependency update infra/helm/xp-mp
helm upgrade --install xp-mp infra/helm/xp-mp \
  --kube-context kind-xp-mp \
  --namespace crossplane-system --create-namespace
```

Adopting it as the install path is a separate decision — the
chart is fit for it but the existing Justfile recipe still works.
