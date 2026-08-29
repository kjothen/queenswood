# The organisation's access groups

<!-- tessl-plugin: deployment -->

## Status

**Verified.** One organisation was set up this way.

## Problem

You have an organisation, and nobody should hold a standing right in
it.

## Solution

### Prerequisites

- An organisation, from [cloud-account](cloud-account.md).
- The super admin, which is what creates groups in the directory.

Four groups, outliving every installation. What an installation needs
beside them is
[queenswood-groups](queenswood-groups.md), and it is per installation
rather than per organisation.

### 1. Create them

In `admin.google.com` under **Directory, then Groups**. Each carries one
capability, and the descriptions below are worth pasting in — a group
whose purpose is not written down acquires members. A description says
what holding the capability lets you do, and never which roles
implement it: those change in a pull request, and nothing goes back to
correct a field in the directory. The display name is the address, so
one string appears on every screen and the console sorts by scope.

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

For each, in this order: **Access type: Restricted**, *then* **Who can
join: Only invited users**. Reversing it discards the join rule, and the
type then reads Custom, which is correct. Leave the access matrix alone.

Create each **without an owner**. An owner is always a member, so owning
the admins group means holding Organization Administrator permanently.
No managers either — administering a privilege-granting group is
privileged, and a super admin can do it without being in the group.

### 2. Bind them

```bash
gcloud config unset project
just gcp-groups-bind
```

Each group against the roles that implement its capability. Billing is
bound on the billing account rather than on the organisation, because
that is where a billing account's policy lives.

## Failures

**A group that plainly exists, reported as "There is no such a group".**
`gcloud identity groups describe` was asked with no active project.
Every Cloud Identity call attributes quota to one, and a read with none
answers as though the group were absent rather than saying so.

## Rules

**MUST:**

- Create every group without an owner or a manager. Both are members.
- Set **Restricted** before **Only invited users**, or the join rule is
  discarded.
- Bind with `just gcp-groups-bind`, from no active project.
- Keep one direct human administrator on the billing account beside its
  group.

**MUST NOT:**

- Leave anybody standing in a break-glass group.
- Use `gcloud identity groups describe` to test whether a group exists.
- Script the creation. Every Cloud Identity write needs a quota project,
  and at foundation time none exists.

## Discussion

The groups are not one per tier of seniority but one per capability that
must be separable. Organization Administrator and Folder Administrator
are two groups because the first cannot delete a folder and the second
cannot touch organisation IAM, and collapsing them would hand out both.

A group owner is always a member, so these have none: administering a
privilege-granting group is itself privileged and belongs with directory
administration, which a super admin holds without being in the group.
That is what makes it safe for the organisation-admin group to be empty
and for nobody to hold Organization Administrator at all — break-glass
is a super admin adding a member, and an empty group is never a lockout.

## References

- [cloud-account](cloud-account.md) — the organisation these belong to.
- [queenswood-groups](queenswood-groups.md) — the four an installation
  adds beside these.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  capabilities and who holds them.
