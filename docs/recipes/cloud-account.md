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

Seven security groups, in `admin.google.com` under Directory then
Groups. Each carries one capability, and the descriptions below are
worth pasting in — a group whose purpose is not written down acquires
members.

Populated, because they are somebody's job:

- **`gcp-platform-operators@`** — Organization Viewer and Browser, plus
  Viewer on the folder, plus the right to impersonate the automation
  service account. *"Day-to-day platform operation. Reads the resource
  hierarchy and the resources inside it, and may act as the automation
  service account rather than holding its rights."*
- **`gcp-security-reviewers@`** — `roles/iam.securityReviewer`.
  *"Read-only view of IAM policy across the organisation. Exists so that
  auditing who holds what never requires the power to change it."*

Empty, because they are capabilities you occasionally need:

- **`gcp-organization-admins@`** — Organization Administrator.
  *"Break-glass for organisation IAM. Join to make a grant only an
  organisation administrator can make, then leave."* Note it cannot
  delete folders.
- **`gcp-folder-admins@`** — Folder Administrator. *"Break-glass for
  creating, moving or deleting a folder. Organization Administrator does
  not carry folders.delete, which is why this is separate."*
- **`gcp-cluster-admins@`** — `roles/container.admin`. *"Break-glass for
  administering Kubernetes clusters directly. Acting on a cluster by
  hand bypasses whatever reconciles it, so joined deliberately."*
- **`gcp-secret-admins@`** — `roles/secretmanager.admin`.
  *"Break-glass for reading and managing secrets. Handling the contents
  of a secret store is a different job from running the infrastructure
  that holds it."*
- **`gcp-billing-admins@`** — Billing Account Administrator.
  *"Administers the billing account: linking projects, budgets,
  payment."* One person also holds this directly, because a billing
  account has no recovery path outside its own IAM policy.

Only the first four have organisation-scoped bindings, made by
`gcp-groups-bind`. Viewer on the folder, `container.admin` and
`secretmanager.admin` are scoped to the folder and the management
project, so they belong in the installation manifest alongside the
resources they apply to, rather than in a recipe.

For each, in this order: **Access type: Restricted**, *then* **Who can
join: Only invited users**. Reversing it discards the join rule, and the
type then reads Custom, which is correct. Leave the access matrix alone.

Then **remove the owner**. An owner is always a member, so owning the
admins group means holding Organization Administrator permanently. No
managers either — administering a privilege-granting group is privileged,
and a super admin can do it without being in the group.

Bind the two organisation roles:

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

Directory then Users. Add it to `gcp-platform-operators@` and
`gcp-billing-admins@` — not the admins group.

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
  --member=group:gcp-billing-admins@yourdomain --role=roles/billing.admin
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

- Leave anybody standing in a break-glass group:
  `gcp-organization-admins@`, `gcp-folder-admins@`,
  `gcp-cluster-admins@`, `gcp-secret-admins@`.
- Give these groups an owner or a manager. Both are members.
- Use `gcloud identity groups describe` to test whether a group exists.

**MAY:**

- Reuse an existing billing account instead of creating one.
- Keep a second super admin, unused, so one lost device is not the end of
  the organisation.

## References

- [cloud-foundation](cloud-foundation.md) — what to do once the
  organisation exists.
