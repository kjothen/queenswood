# Cloud account

<!-- tessl-plugin: deployment -->

## Problem

You have no Google Cloud. An organisation comes from Cloud Identity
rather than from Google Cloud, and none of this step has an API.

## Solution

### Before you start

- A domain, with access to edit its DNS.
- A recovery email and phone for the admin account. It gets no mailbox,
  so it cannot receive its own password reset.
- A private browser window. Signing up as an existing Google account is
  the usual way this goes wrong.

### 1. Sign up for Cloud Identity Free

At `workspace.google.com/gcpidentity/signup?sku=identitybasic`. Google
moves that page; if it steers you to a paid Workspace plan, find the free
Cloud Identity edition instead.

Use `admin@yourdomain` for the admin. It is a new Google account,
unrelated to your existing one.

### 2. Verify the domain

Google may hand off to your registrar and add the TXT record itself.
Success reads "You're all set to use Google Workspace apps" — the
branding is shared with Workspace and says nothing about your edition.

### 3. Check the edition

`admin.google.com`, Billing then Subscriptions: Cloud Identity Free, 50
licences. A Workspace trial expires and takes the account with it.

Turn on 2-step verification here, and store the password and backup
codes. This account is the root of trust for everything below.

### 4. Create the organisation

Sign in to `console.cloud.google.com` as the admin and accept the terms.
The organisation appears on that first sign-in — no project needed — and
the console grants the admin Organization Administrator in the same
action.

### 5. Create the access groups

Eight security groups, in `admin.google.com` under Directory then
Groups. Each carries one capability, and the descriptions below are
worth pasting in — a group whose purpose is not written down acquires
members. A description says what holding the capability lets you do, and
never which roles implement it: those change in a pull request, and
nothing goes back to correct a field in the directory. The display name
is the address, so one string appears on every screen and the console
sorts by scope.

Four at the organisation, which outlive every installation:

- **`grp-gcp-org-admin@`** — Empty. *"Grants and revokes IAM across the
  organisation. Break-glass: join for the task, then leave."*
- **`grp-gcp-folder-admin@`** — Empty. *"Creates, moves and deletes
  folders. Break-glass: join for the task, then leave."*
- **`grp-gcp-billing-admin@`** — Empty. *"Administers the billing account
  — linking projects, budgets and payment. Break-glass: join for the
  task, then leave. One person also holds this directly, because a
  billing account has no recovery path outside its own policy."*
- **`grp-gcp-security-reviewer@`** — Populated. *"Reads IAM policy across
  the organisation and changes nothing. Populated: auditing who holds
  what must never require the power to change it."*

Four per installation, coded to it and deleted with it — `qw01` here:

- **`grp-gcp-qw01-platform-viewer@`** — Populated. *"Reads qw01 and
  everything inside it, and writes nothing. Populated: this is
  day-to-day operation."*
- **`grp-gcp-qw01-platform-admin@`** — Empty. *"Assumes the identity that
  runs qw01, which administers all of it. Break-glass: join for the
  task, then leave."*
- **`grp-gcp-qw01-cluster-admin@`** — Empty. *"Administers qw01's
  Kubernetes clusters directly. Break-glass: join for the task, then
  leave. Acting on a cluster by hand bypasses whatever reconciles it."*
- **`grp-gcp-qw01-secrets-admin@`** — Empty. *"Reads and manages qw01's
  secrets. Break-glass: join for the task, then leave. Handling secret
  contents is a different job from running the infrastructure that holds
  them."*

Only the organisation set is bound by `gcp-groups-bind`, and billing is
bound on the billing account rather than on the organisation. The one
exception is `grp-gcp-qw01-platform-viewer@`, which takes Organization
Viewer and Browser there too: both are hierarchy metadata, and the
tooling cannot reach a folder without first resolving the organisation
holding it. The rest of the installation set is folder and project
scoped, so it belongs in the installation manifest beside the resources
it applies to. The manifest names each capability and maps it to a
principal, so which group answers is configuration; see
[ADR-0023](../adr/0023-installation-naming-and-access.md).

For each, in this order: **Access type: Restricted**, *then* **Who can
join: Only invited users**. Reversing it discards the join rule, and the
type then reads Custom, which is correct. Leave the access matrix alone.

Create each **without an owner**. An owner is always a member, so owning
the admins group means holding Organization Administrator permanently.
No managers either — administering a privilege-granting group is
privileged, and a super admin can do it without being in the group.

Bind the organisation roles:

```bash
gcloud config unset project
just gcp-groups-bind
```

Creating groups cannot be scripted here: every Cloud Identity call needs
a project to attribute quota to, taken from the active gcloud project,
and none exists yet. Beware that without one,
`gcloud identity groups describe` answers "There is no such a group" for
groups that plainly exist.

### 6. Create the operating user

Directory then Users. Add it to `grp-gcp-qw01-platform-viewer@` and
nothing else — not a break-glass group, and not the billing group, which
stays empty beside your own direct binding.

It needs no direct organisation bindings: Project Creator and Billing
Account Creator are already granted to the whole domain, and the rest
arrives through membership. Prefer a domain user over a personal address,
which `iam.allowedPolicyMemberDomains` would later invalidate.

### 7. Create the billing account

As the operating user, so that user administers it. Take the free trial
if offered; some payment methods are asked for a small refundable
prepayment first.

Two traps. Card verification runs through 3-D Secure, which private
windows break. And whether the payments profile is for an individual or
an organisation cannot be changed afterwards — pick organisation only if
a registered entity exists to name, matching the payment instrument.

Then bind the group, and keep your own direct binding:

```bash
gcloud billing accounts add-iam-policy-binding <account-id> \
  --member=group:grp-gcp-billing-admin@yourdomain --role=roles/billing.admin
```

A billing account has no recovery path outside its own IAM policy, so
never remove its last human administrator. This is the one place a person
is bound directly on purpose.

### 8. Put the standing grants away

Once the group carries Organization Administrator, remove the admin
account's direct binding, then revoke it locally:

```bash
gcloud auth revoke admin@yourdomain
```

Until you do, `gcloud config set account` reaches super admin with no
password, no prompt and nothing in the audit log.

### 9. Check

```bash
gcloud auth login
just gcp-preflight
```

An organisation and a billing account is enough to continue with
[cloud-foundation](cloud-foundation.md). Run as the operating user, the
roles line reads "not readable by this account" — Organization Viewer
excludes `getIamPolicy`, and group-derived roles never appear there.

## Rules

**MUST:**

- Set recovery email and phone on the super admin, and 2-step
  verification. It has no mailbox and no one above it.
- Bind groups where humans hold access, and principals directly where
  automation does.
- Keep one direct human administrator on the billing account.
- Revoke the super admin's local credentials, and its direct organisation
  binding, once the group carries the role.

**MUST NOT:**

- Leave anybody standing in a break-glass group: `grp-gcp-org-admin@`,
  `grp-gcp-folder-admin@`, `grp-gcp-billing-admin@`,
  `grp-gcp-qw01-platform-admin@`, `grp-gcp-qw01-cluster-admin@`,
  `grp-gcp-qw01-secrets-admin@`.
- Give these groups an owner or a manager. Both are members.
- Use `gcloud identity groups describe` to test whether a group exists.

**MAY:**

- Reuse an existing billing account instead of creating one.
- Keep a second super admin, unused, so one lost device is not the end of
  the organisation.

## References

- [cloud-foundation](cloud-foundation.md) — what to do once the
  organisation exists.
