# Provision a GCS bucket for widget file storage

## Background

`inputs/infra/crossplane/vpc.yaml` is the existing declaration for
the platform's VPC — one of the cloud resources Argo CD continuously
reconciles from this repo. The platform needs a new GCS bucket for
widget file storage, applied the same way.

## Task

Add `inputs/infra/crossplane/widget-bucket.yaml`, declaring the
bucket the same way `vpc.yaml` declares the VPC — matching its
`apiVersion` family, its `providerConfigRef`, and its
`spec.forProvider` shape (for a bucket: at minimum a `location`).

Create `inputs/infra/crossplane/widget-bucket.yaml`.
