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
first grant.

That role stays on the admin account as break-glass, and is used
deliberately for the few grants only it can make — chiefly the one that
lets the bootstrap identity create the folder.

### 6. Create an operating user

Do not work as the super admin. In `admin.google.com`, under Directory
then Users, add a second domain user for day-to-day operation.

It needs strikingly little at the organisation. Project Creator and
Billing Account Creator are already granted to the whole domain — the
organisation's IAM page lists them against a principal named for the
domain itself rather than any person — and
everything else arrives by impersonating the bootstrap identity. Add
Organization Viewer so the console is navigable and so `gcp-preflight`
can read the organisation as that user — granting it at the organisation
scope, selected before navigating to IAM.

Prefer a domain user over an external account for this. A personal
address works today, but `iam.allowedPolicyMemberDomains` — a policy
plenty of organisations eventually enforce — invalidates every binding
that names one.

### 7. Create a billing account

Requires a payment method. Create it as the operating user, so that user
becomes its administrator and can link projects without a further grant.
Take the free trial if it is offered — it is once per identity and
payment instrument, so an earlier trial elsewhere means it won't be.
Some payment methods are asked for a small refundable prepayment first,
which is credited to the billing account rather than charged, and the
trial credits follow once it clears.

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

### 8. Check it from the CLI

```bash
gcloud auth login
gcloud organizations list
just gcp-preflight
```

`gcp-preflight` reports the organisation and its Cloud Identity
customer, the billing account, and which organisation roles are bound
directly to you. An organisation and a billing account is what it takes
to continue with [cloud-foundation](cloud-foundation.md).

Run as the operating user, the roles line reads "not readable by this
account". That is correct and blocks nothing: Organization Viewer does
not include `getIamPolicy`, and domain-wide grants would not be listed
there anyway, because they name the domain rather than a person. Run it
as the admin account to see the organisation's bindings.

Domain ownership is not checked. The only thing that needs it is
creating a public DNS zone, which happens per instance, much later.

## Rules

**MUST:**

- Set recovery email and phone on the super admin. It has no mailbox of
  its own.
- Sign up in a private window, not as an existing Google account.
- Grant the Organization Administrator role explicitly. Super admin
  status does not include it.

**MUST NOT:**

- Use the super admin for day-to-day work. It bypasses SSO by design and
  exists for recovering access.
- Bind roles to a personal email address when a domain user will do.

**MAY:**

- Reuse an existing billing account rather than creating one, if you
  already have one with a payment method.

## References

- [cloud-foundation](cloud-foundation.md) — what to do once the
  organisation exists.
