# Cloud account

<!-- tessl-plugin: deployment -->

## Problem

You have no Google Cloud at all, and everything else assumes an
organisation. This is the one part done by hand, in a browser.

## Solution

An organisation is not a resource you create. It appears when a domain
is claimed through Cloud Identity, so this recipe is clicks rather than
commands. Everything after it is declarative — see
[cloud-foundation](cloud-foundation.md).

### Before you start

- **A domain you control**, and access to edit its DNS records. Any
  registrar.
- **A recovery email and phone** for the admin account. Cloud Identity
  Free gives no mailbox, so `admin@yourdomain` cannot receive its own
  password reset. Setting recovery options wrong is how people lock
  themselves out of a brand-new organisation permanently.
- **A private browser window.** Signing up while logged into a personal
  Google account is the most common way this goes wrong.
- **Decide the admin address** — `admin@yourdomain` is conventional.
  This becomes a new Google account, unrelated to your existing one.

No MX change is needed and no mail is involved: verification is a single
TXT record, and Cloud Identity Free does not provide mailboxes.

### 1. Sign up for Cloud Identity Free

Cloud Identity's free edition is what creates the organisation. Reach it
at `workspace.google.com/gcpidentity/signup?sku=identitybasic` — Google
moves this page periodically, so if it redirects to a paid Workspace
plan, look for the free "Cloud Identity" edition rather than accepting
Workspace.

You will be asked for the domain, a name, the admin address chosen
above, and recovery details.

### 2. Verify the domain

Google may hand off to the registrar and add the record for you — with
Squarespace it redirects, asks you to authorise, and comes back
verified. Otherwise it issues a `google-site-verification=...` string to
add as a TXT record on the apex yourself. Propagation is usually
minutes.

Success is announced as "You're all set to use Google Workspace apps",
and the console offers to add team members and explore premium
features. Ignore both. Cloud Identity Free is administered through the
same console as Workspace, so the branding says nothing about which
edition you are on — which step 3 checks.

### 3. Confirm which edition you are on

At `admin.google.com`, under Billing then Subscriptions, it should read
Cloud Identity Free, active, on the free plan, with 50 user licences
available. If it names a Workspace plan or trial instead, that trial
expires and takes the account with it, so switch or add Cloud Identity
Free before going further.

While you are there, turn on 2-step verification for the admin account
and store its password and recovery codes.

### 4. Let the organisation appear

Sign in to `console.cloud.google.com` as the new admin account and
accept the terms. The organisation is provisioned on that first sign-in
and appears under IAM & Admin then Manage Resources as the root node,
with a numeric id. No project has to be created first.

The console does this as one action and grants the admin account
Organization Administrator at the same time — the activity entry reads
"Created <domain> organization and granted <admin> the organization
admin role".

### 5. Confirm you hold Organization Administrator

Being a Cloud Identity super admin is not the same as holding IAM roles,
and none of the resource manager roles the foundation needs come with
it. Step 4 normally grants the role, so this is usually a check.

Pick the organisation in the resource selector at the top of the console
first, then go to IAM & Admin then IAM — the selector scopes the console,
so choosing it afterwards is the wrong way round. It defaults to a
project, and there is no project yet, so a page that looks empty is that
selector rather than a permissions problem.

If the role is absent, grant it there. Only a super admin can make this
first grant. It is also the last thing this account does directly:
from here on, roles bind to groups.

### 6. Create the access groups

This is the longer road rather than the quick one, and it is worth
taking. The quick version binds roles to your own account and moves on.
The trouble arrives later, when the way to reduce your own access is to
edit an IAM policy — so nobody does, and the rights accumulate.

Instead, roles bind to groups and never move again. Membership becomes
the only thing that changes, which is one place to look, one thing to
revoke, and recorded in the Admin audit log. It is also what access
tooling automates: Teleport and the like grant a role by adding you to a
group for as long as you need it, then taking you out again. Doing it
this way now means that story is available later without redoing the
foundation.

Create the three groups in `admin.google.com`, under Directory then
Groups. This part cannot be scripted, and the reason is worth knowing
because the error hides it: every Cloud Identity call needs a project to
attribute quota to, taken from the **active gcloud project** — not from
ADC's quota project, which has no effect here. At this point no project
exists, so there is nothing to name. IAM bindings need none, which is
why the binding half is a recipe.

Worse, without a usable quota project `gcloud identity groups describe`
answers "There is no such a group" rather than reporting an API problem,
so a group that plainly exists reads as missing. Do not use it to check
whether these groups are there.

Once a project does exist, the CLI becomes usable — enable the API on it
and make it active:

```bash
gcloud services enable cloudidentity.googleapis.com --project=<project>
gcloud config set project <project>
```

Three groups, and **remove the owner after creating each one**. A group
owner is always a member of it, so owning the admins group means holding
Organization Administrator permanently — the standing authority the
groups exist to remove, wearing a different hat. The console adds the
creator as owner, and it will let you take them out again, leaving a
group with no members at all.

No managers either, for the same reason: both roles make the holder a
member. Managing the membership of a privilege-granting group is itself
a privileged act, so it belongs with directory administration rather
than inside the group — a super admin can manage any group without
being in it.

That does not have to mean a super admin forever. Workspace has a
pre-built **Groups Admin** role, far short of super admin, and custom
admin roles can carry the group privileges alone. That is the seam an
access tool uses: hold the privilege to administer groups, not
membership of the groups being administered.

- **Organization admins**, `gcp-organization-admins@` — Organization
  Administrator on the organisation. Normally empty. Joining it is the
  break-glass act.
- **Platform operators**, `gcp-platform-operators@` — Organization
  Viewer, plus the right to impersonate the bootstrap identity.
- **Billing admins**, `gcp-billing-admins@` — Billing Account
  Administrator, bound on the billing account in step 8 rather than on
  the organisation.

The access settings matter more than the names. The console defaults to
**Public**, which combined with its default of "anyone in the
organization can join" lets any domain user add themselves — to the
admins group as readily as any other, which would hand out Organization
Administrator on request.

Order matters here. Choose **Access type: Restricted** first — it is a
preset over the access matrix and rewrites it — and only then set **Who
can join the group** to *Only invited users*. Setting the join rule
first and picking the preset afterwards discards it, silently.

The type then reads **Custom**, because a per-field change relabels it.
That is the correct end state, not a misconfiguration, and worth knowing
before someone tidies it back to Restricted and re-opens self-join.

Leave external members unchecked, and check that **who can manage
members** stops at owners and managers rather than reaching group
members or the whole organisation. The grid itself needs no editing.

Then apply the **Security** label. Every Google group is a mailing list
underneath, so the Mailing label cannot be removed — Security is added
alongside it, and marks the group as something policies attach to.

Then bind the two organisation roles:

```bash
gcloud config unset project
just gcp-groups-bind
```

`gcp-groups-bind` verifies each group exists and binds its role. Unset
the project first: a project the caller cannot use is attached to
Cloud Identity calls as quota attribution and denies them, and no
foundation step needs one.

Break-glass is now joining `gcp-organization-admins@` deliberately,
doing the one thing that needs it, and leaving. A Workspace super admin
can always add a member, so an empty group is never a lockout.

### 7. Create an operating user

Do not work as the super admin. In `admin.google.com`, under Directory
then Users, add a second domain user for day-to-day operation, then add
it to `gcp-platform-operators@` and `gcp-billing-admins@` — in the
console, for the same reason the groups were made there.

Once a project exists, membership can move to the CLI:

```bash
gcloud auth application-default set-quota-project <project>
just gcp-access-grant gcp-platform-operators you@yourdomain
just gcp-access-revoke gcp-organization-admins you@yourdomain
```

That is the pair worth having later, because granting and revoking
access becomes two commands an access tool can call.

It needs no direct organisation bindings at all. Project Creator and
Billing Account Creator are already granted to the whole domain — the
organisation's IAM page lists them against a principal named for the
domain itself rather than any person — and everything else arrives
through group membership or by impersonating the bootstrap identity.

Prefer a domain user over an external account. A personal address works
today, but `iam.allowedPolicyMemberDomains` — a policy plenty of
organisations eventually enforce — invalidates every binding that names
one.

### 8. Create the billing account

Requires a payment method. Create it as the operating user, so that user
becomes its administrator. Take the free trial if it is offered — it is
once per identity and payment instrument, so an earlier trial elsewhere
means it won't be. Some payment methods are asked for a small refundable
prepayment first, which is credited to the billing account rather than
charged, and the trial credits follow once it clears.

Card verification runs through a 3-D Secure provider, which private
browsing windows tend to break. Use an ordinary window — ideally a
browser profile kept signed in as this user, since three identities are
now in play.

The form asks whether the payments profile is for an individual or an
organisation, and that choice cannot be changed once the profile is
created — correcting it later means a second profile and re-pointing
every project. Pick organisation only if a registered entity exists to
name, and give its exact legal name, matching the payment instrument. A
personal profile limits nothing in Google Cloud, and a business account
can be added later with projects re-pointed at it one by one.

Then bind the group, as the operating user, since only an administrator
of the account can:

```bash
gcloud billing accounts add-iam-policy-binding <account-id> \
  --member=group:gcp-billing-admins@yourdomain --role=roles/billing.admin
```

**Keep the operating user's own administrator binding as well.** This is
the one deliberate exception to binding groups rather than people, and
the reason is asymmetry in the escape hatches. The organisation has one
outside IAM: a Workspace super admin can always re-grant Organization
Administrator, which is what makes an empty admins group safe. A billing
account has nothing equivalent — super admin status grants nothing on
it. Strip its last direct administrator while group resolution is
misbehaving and recovery is a support ticket, not a command you can run.

### 9. Put the standing grants away

The point of the previous steps is undone if the direct bindings stay.
As the super admin, remove the direct Organization Administrator binding
from the admin account, having first confirmed `gcp-organization-admins@`
carries it.

Then revoke the admin account's credentials locally:

```bash
gcloud auth revoke admin@yourdomain
```

Until you do, that account's refresh token sits in the same credential
store as your everyday one, and `gcloud config set account` moves
between them with no password, no prompt and nothing in the Admin audit
log. Break-glass that is one command away from any process running as
you is not break-glass. Afterwards, using it costs a deliberate sign-in
with 2-step verification.

### 10. Check it from the CLI

```bash
gcloud auth login
gcloud organizations list
just gcp-preflight
just gcp-access-status
```

`gcp-preflight` reports the organisation and its Cloud Identity
customer, the billing account, and which organisation roles are bound
directly to you — which should now be none, because they come through
groups. An organisation and a billing account is what it takes to
continue with [cloud-foundation](cloud-foundation.md).

Run as the operating user, the roles line reads "not readable by this
account". That is correct and blocks nothing: Organization Viewer does
not include `getIamPolicy`, and neither domain-wide nor group-derived
grants would be listed there anyway, because they do not name a person.

Domain ownership is not checked. The only thing that needs it is
creating a public DNS zone, which happens per instance, much later.

## Rules

**MUST:**

- Set recovery email and phone on the super admin. It has no mailbox of
  its own.
- Sign up in a private window, not as an existing Google account.
- Bind groups where humans hold access, and principals directly where
  automation does. Membership is the lever for people; a service account
  gets its roles bound straight to it.
- Keep one direct human administrator on the billing account. It has no
  recovery path outside its own IAM policy.
- Revoke the super admin's credentials locally once it is done, and
  remove its direct organisation binding.

**MUST NOT:**

- Leave anybody standing in `gcp-organization-admins@`. Joining it is
  the break-glass act, and it is logged.
- Use the super admin for day-to-day work. It bypasses SSO by design and
  exists for recovering access.
- Bind roles to a personal email address when a domain user will do.

**MAY:**

- Reuse an existing billing account rather than creating one, if you
  already have one with a payment method.
- Add further groups per job as the installation grows. The bindings
  they carry are what makes them worth having, not the names.

## References

- [cloud-foundation](cloud-foundation.md) — what to do once the
  organisation exists.
