# Scanning the organisation, and what it is allowed to find

<!-- tessl-plugin: deployment -->

## Problem

A benchmark scan reports findings, and some of them describe decisions
rather than defects. Left unmarked, each one is rediscovered and
re-argued at the next scan; marked carelessly, a real finding is muted
along with them.

## Solution

Scan the whole organisation, and keep the accepted findings in a file
beside the reasoning, so an exception is a decision somebody made rather
than a check somebody switched off.

### Running it

```bash
just gcp-scan
```

Prowler against the organisation with the CIS benchmark and the
mutelist. Scanning needs `roles/iam.securityReviewer` and
`roles/cloudasset.viewer` at the organisation, both carried by
`grp-gcp-security-reviewer@`, which is populated — auditing requires no
break-glass.

Two things are worth knowing before reading a report:

- **It authenticates through application-default credentials**, not
  through `gcloud`'s own login. Where ADC impersonates the bootstrap
  identity — which `gcp-adc-boot` arranges — the scan runs as that
  identity rather than as you, and stops working the moment you leave
  the group that permits the impersonation.
- **Discovery is the Asset API.** Without `cloudasset.viewer` the scan
  cannot enumerate projects and falls back to whatever it is given,
  which for an installation that creates a project per instance is
  wrong the moment an instance exists.

### What is muted, and why

Both entries name resources rather than checks, so the same check still
fails for anything they do not cover.

**CIS 1.6 — service accounts with administrative privileges**, for the
platform and bootstrap identities. Both hold admin by design: the
platform identity reconciles the installation and administering every
cluster in the folder is its purpose, and the bootstrap identity creates
the management project. Neither is held by a person, neither has a key,
and who may impersonate them is group-bound and break-glass. A third
service account acquiring admin still fails.

**CIS 2.14 — Cloud Asset Inventory enabled**, for every project. The
capability is already organisation-wide: Google collects asset metadata
for every resource whether or not a project has the API on, and querying
it needs the API on one project — the caller — plus `cloudasset.viewer`
at the organisation. The check tests a per-project enablement that
changes nothing about what is collected or what can be seen, so
satisfying it would alter the report and nothing else.

That distinction is the one to hold on to when muting anything else.
1.6 is muted because the finding is accurate and the answer is "yes, on
purpose". 2.14 is muted because the check is a poor proxy for the thing
it is named after. Neither is muted because the fix is inconvenient.

### What is deferred, and why that is different

These are real, unmuted, and expected to keep failing until somebody
decides otherwise. They are not defects to be surprised by:

- **CIS 2.1, audit logging** — data-access logs are off. Enabling them
  is a genuine control and a genuine bill, since log volume is charged.
- **CIS 3.10, VPC flow logs** — same shape, and the more expensive of
  the two on a busy cluster. Sampling is the middle option.
- **CIS 4.9, the management node's public IP** — the cluster is publicly
  reachable, which
  [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  leaves deliberately open.
- **CIS 4.7 and 4.11, CSEK disks and Confidential Computing** — both
  Level 2, both a real cost, for a cluster running Crossplane and Argo
  and nothing else.

What this installation demonstrates is how an organisation is built,
which is not the same as running one commercially. A control with a
standing cost is deferred until something is running that justifies it,
and the entry above is what stops that being mistaken for an oversight.
Deferred is written down; accepted is in the mutelist; neither is
silence.

### Adding an exception

Add the resources to `infra/security/prowler-mutelist.yaml` with a
`Description`, and the reasoning here. A mutelist entry with no
corresponding paragraph is indistinguishable from someone quietening a
report, which is what the next reader will assume.

## Rules

**MUST:**

- Mute by resource, so the check still fails for anything the reasoning
  does not cover.
- Record the reasoning here as well as in the file's `Description`.
- Say what is deferred and why, since a finding nobody has explained is
  a finding nobody has decided.

**MUST NOT:**

- Mute a finding because fixing it is inconvenient. Cost is a reason to
  defer in the open, not to hide.
- Treat a falling finding count as the objective. Enabling an API in
  every project to satisfy a per-project check that describes an
  organisation-wide capability changes the report and nothing else.
- Assume a scan ran as you. It authenticates through ADC, which may be
  impersonating something else entirely.

## References

- [cloud-account](cloud-account.md) — the organisation's groups, and
  which of them are populated rather than break-glass.
- [gcp-iam](gcp-iam.md) — why reading organisation policy needs a role
  nobody holds by default, and the two generations of constraint id.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the access
  capabilities and who holds which.
