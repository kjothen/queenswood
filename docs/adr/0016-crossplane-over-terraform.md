# 16. Crossplane over Terraform for infrastructure

<!-- tessl-plugin: deployment -->

## Status

Accepted. The choice of Crossplane stands; the management plane it
describes — a local kind cluster — is superseded by
[ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md).

## Context

Queenswood provisions a non-trivial amount of cloud infrastructure
to stand up: a VPC and proxy-only subnet, a GKE cluster, a static
ingress IP, a Cloud DNS managed zone with apex + subdomain
records, a Google-managed certificate, a CloudSQL Postgres
instance, the GKE-side Helm releases that put workloads on it.
Two reasonable shapes for declaring all of that are Terraform
(industry standard) and Crossplane (Kubernetes-native).

## Decision

We use Crossplane. Concretely:

- A small local kind cluster (`boot-mgmt`, the boot management
  plane) runs
  Crossplane plus the GCP family of upjet providers and
  provider-helm.
- Every cloud resource is a Crossplane Managed Resource or a
  Composite of them (XRDs + Compositions).
- Argo CD on the same kind cluster applies the manifests from
  the repo. Workloads on GKE are themselves Crossplane
  `Release` resources of provider-helm.

## Consequences

What we gain over Terraform:

- **GitOps-native.** Argo + Crossplane mean the desired state in
  the repo is continuously reconciled against the live cloud.
  Terraform's `apply` is a point-in-time event; drift between
  applies is invisible until the next `plan`.
- **Single API surface.** `kubectl get` works for a VPC, a GKE
  cluster, a `Release` on that cluster, and the Pods inside the
  release. Operators don't switch tools between layers.
- **Live overrides via kubectl.** Patching a Managed Resource
  flows through to the cloud immediately — useful when
  debugging or when an emergency change needs to ship before
  the chart can catch up.
- **Same RBAC and audit story** as the rest of the cluster.

What we lose:

- **Provider maturity.** Terraform's GCP provider is older and
  has more in-tree coverage than provider-upjet-gcp. We've hit
  edge cases — v1 vs v2 ProviderConfig semantics,
  `LegacyCluster`-only connection secrets, schema differences
  between v1beta1 and the upjet v2 `*.gcp.m.upbound.io` API
  group. Mitigation: pin providers and Compositions in the
  repo; keep Compositions to the patch-and-transform pipeline
  rather than custom KCL/Go.
- **Smaller community.** Less Stack Overflow, fewer modules to
  copy from. Mitigation: this is bounded by what we actually
  use (GCP only, no AWS/Azure spread).
- **Tooling.** No `terraform plan` equivalent for previewing a
  diff before it applies. Mitigation: Argo's diff view + the
  composite's `status.conditions` give acceptable visibility.

The [infrastructure TDD](../tdd/infrastructure.md) describes how
the pieces fit together — which Composites we own, how the
management plane bootstraps GKE, the gcp-down teardown, the
patterns we keep coming back to.
