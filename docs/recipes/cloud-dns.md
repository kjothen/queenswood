# Cloud DNS
<!-- tessl-plugin: deployment -->

## Problem

The zone and the records in it are composed, but two things around them
are not. A public zone cannot be created by an identity that has not
proved it owns the domain, and the delegation that sends the internet
to that zone is edited wherever the domain was bought. Neither has an
API the plane can reach, so both are done once, by hand, before the
declared side means anything.

This is that half. Where the zone lives and what composes the records
in it belongs to the composition, not here.

## Solution

### Before you start

- The domain, and access to change its nameservers at the registrar.
- The installation's management project, which is where its zone goes.
- Whichever identity will create the zone — yours if you are creating
  it by hand, the platform identity if the composition is.

### 1. Verify the domain

Cloud DNS refuses to create a public zone whose name the calling
identity has not verified, and reports
`verifyManagedZoneDnsNameOwnership` when it does. The check is against
Google Site Verification, not against anything in Cloud DNS — Cloud DNS
only consults it.

Verification is a TXT record. Google issues a token, the token goes in
the domain's DNS as `google-site-verification=<token>` at the apex, and
Google reads it back. It is managed at
`search.google.com/search-console`, and `gcloud domains verify <domain>`
opens whichever page is current, which is the durable form — Google
moves these. A registrar offering one-click DNS writes the record for
you rather than making you paste it, and that is all that widget does.

Two neighbouring permission systems are not this one. The Admin console
manages Cloud Identity's own domains. The registrar has an account model
of its own, granting people the right to edit DNS and move the
registration. Neither grants ownership in Google's sense, and nothing
the installation composes needs an account at either — the registrar is
touched by hand twice, to add a TXT record and to change the
nameservers, and never by an identity.

Four things about it are easy to get wrong.

**Verification belongs to an identity, not to a domain.** A token
placed by one Google account verifies that account, so a
`google-site-verification` record already at the apex is no evidence of
who owns it. An empty property list is no evidence of the reverse
either: Search Console shows properties somebody added, and the
verification Cloud Identity performs at signup is administered from the
Admin console and creates no property at all, so a domain that already
carries a token can be absent from Search Console entirely. Add it as a
Domain property rather than a URL-prefix one — a Domain property covers
every subdomain, which saves verifying each environment separately. See
[cloud-account](cloud-account.md).

**A method can be tied to the registrar rather than to a record.**
Google may auto-verify a domain by recognising who provides its DNS,
reporting the method as `Domain name provider`. That verification rests
on the provider relationship, and the delegation change below ends
it — leaving the domain un-verified for reasons that surface much later
as a zone create or a reconcile failing. Add the DNS TXT method
explicitly under Settings, then Ownership verification, before the
delegation moves. It yields a token in a record you control, which is
what step 4 carries into the new zone, and verification then no longer
depends on who serves the domain.

**Delegated ownership lapses with the token it hangs off.** An owner may
add owners, so a verified account of any kind is enough to unblock the
rest. But an installation whose identities are all delegated from
somebody's personal account loses the domain when that account is tidied
up, and it surfaces as `verifyManagedZoneDnsNameOwnership` on a zone
create, which reads like a Crossplane fault rather than an account one.
Have the installation's own operator account verify in its own right and
delegate from there. A domain carries several `google-site-verification`
records without conflict, so that is an addition rather than a
migration.

**The composition does not run as you.** The plane authenticates through
Workload Identity, so the provider creates the zone as the platform
service account, and a service account is never a verified owner by
default. Grant it ownership under Settings, then Users and permissions,
then Manage property owners, whose Add an owner box takes a service
account address like any other. The property has to be verified before
it has owners to add. Where that is not wanted, create the zone yourself
as a verified human and let the composite adopt it.

### 2. Inventory the registrar

Before moving anything, find out what the domain is actually serving.
The records that matter are rarely the ones it is visibly for.

```
for r in NS A AAAA MX TXT CAA DS SOA; do
  echo "-- $r"; dig +short $r <domain>
done
for n in _dmarc _domainconnect _acme-challenge; do
  echo "-- $n"; dig +short ANY $n.<domain>
done
```

A sweep of the apex alone misses the records that matter most, because
the ones carrying policy sit under names nothing advertises. `_dmarc`
is the other half of an SPF lockdown and is invisible from the apex.
`_domainconnect` is the registrar's one-click DNS endpoint, which is
what a widget like Entri drives; it names the old provider and does not
carry.

Decide record by record what carries. A placeholder site's A records
and `www` CNAME describe a page nobody depends on and can be dropped.
An unattributed verification token is not in that category: the Admin
console reports whether a domain is verified but not which record
satisfies it, so a second token cannot be told apart from the one
holding the organisation's primary domain. Carry both — the cost is a
record, and the cost of guessing wrong arrives days later as a
re-check nothing connects to the edit.
Anything encoding a claim or a policy has to survive: the
verification TXT above, whose loss can cost the organisation its
domain, and an SPF record, whose `-all` is a standing statement that
the domain sends no mail.

### 3. Read the zone's nameservers

Once the plane has composed the zone and it reports `Ready`, Cloud DNS
has assigned it four nameservers. They are not predictable and they
are not the same for two zones.

```
kubectl --context <plane> get managedzone <zone> \
  -o jsonpath='{.status.atProvider.nameServers}'
```

### 4. Populate and check before delegating

The verification token lives wherever the domain's nameservers say it
lives. Until the delegation moves that is the registrar, and afterwards
it is this zone — so the same token has to exist in both before the
switch, or verification lapses the moment the registrar stops being
asked. It is the same string throughout. Nothing is regenerated.

Put the carry-over records into the new zone, then query its
nameservers directly rather than through a resolver, which would still
answer from the old authority.

```
dig @<assigned-nameserver> <domain> TXT +short
```

Diff that against what the registrar still serves. Both sides should
answer identically for everything that carries. Done in this order the
propagation window is a no-op, because there is no interval in which
the two authorities disagree.

### 5. Unsign before delegating

A signed domain publishes a DS record at its registry naming the keys
its current nameservers sign with. Cloud DNS signs with different keys,
so moving the delegation while that DS stands makes every validating
resolver treat the new authority's answers as forged — not a stale
record served for a while, but SERVFAIL everywhere at once, taking the
verification TXT down with it and with it the ownership the zone rests
on.

The mismatch is not read as unsigned-and-therefore-fine. DNSSEC fails
closed there deliberately, since the alternative would let an attacker
strip signatures to downgrade a protected domain. This is also the one
failure the diff in step 4 does not protect against: that makes both
authorities agree on content, and the parent's assertion is not about
content.

Keeping it signed across the move is not available. It would need the
new provider's keys pre-published in the old zone, and Cloud DNS
generates and owns its keys rather than accepting an existing one, so
unsign, move, re-sign is the path.

```
dig +short DS <domain>
```

An answer means DNSSEC is on. Turn it off at the registrar, since the DS
lives at the parent registry and only a registrar withdraws it, then
wait out the DS record's own TTL before touching the nameservers — the
registry's TTL, not the zone's, because a resolver still holding a
cached DS goes on expecting signatures after the registry has dropped
it.

Expect the outage to begin at the disable rather than at the
delegation. A registrar may strip the zone's keys at once and submit
the DS withdrawal to the registry afterwards, which puts the domain in
exactly the mismatched state above for as long as that takes — hours,
against a published bound of days. So do this while an outage is
affordable, which for a domain not yet serving anything is now rather
than later, and watch the parent rather than the zone:

```
dig +short DS <domain> @<registry-nameserver>
```

Empty is the signal. The delegation waits on that, plus the TTL.
Re-sign on the new zone afterwards if wanted, which means publishing a
fresh DS at the registrar once Cloud DNS has its keys.

### 6. Change the delegation

At the registrar, replace the nameservers with the four assigned ones.
This is the act that moves the domain, and it is the last time the
registrar is touched — after it, every name in the domain is declared
in the manifest rather than typed into somebody's account.

A registrar serving its own DNS stops doing so at this point. Anything
of its that was not carried in step 2 stops resolving here.

### 7. Check

Resolve each name through a public resolver, and confirm the
verification TXT still answers from the new authority. The
organisation rests on that record, and it is the one whose absence
does not show up as a broken page.

Set a CAA record naming the issuing CA once it is known. An absent CAA
authorises every CA rather than none.

## Rules

**MUST:**

- Verify domain ownership before a public zone is created, and add the
  platform identity as an owner — not Full or Restricted, which grant
  report access and confer no ownership — if the composition creates
  the zone.
- Add the DNS TXT verification method explicitly where the domain was
  auto-verified through its provider, before the delegation moves.
- Inventory every record type at the registrar before moving a domain.
- Carry the ownership-verification TXT and any SPF record into the new
  zone before the delegation moves.
- Query the assigned nameservers directly to check the new zone, not a
  public resolver.
- Check for a DS record before delegating, and where one exists disable
  DNSSEC at the registrar and wait out the DS TTL first.
- Sweep the underscore-prefixed names as well as the apex. `_dmarc`
  carries policy and the apex sweep does not show it.
- Confirm the verification TXT resolves from the new authority after
  the delegation moves.

**MUST NOT:**

- Assume a placeholder site's records are all that is there. Mail
  policy and verification tokens carry no visible page.
- Read an existing `google-site-verification` record as evidence that
  your account owns the domain. It verifies whoever placed it.
- Read an absent Search Console property as evidence the domain is
  unverified. Cloud Identity's own verification creates no property.
- Leave the installation's identities delegated from a personal
  account. Delegated ownership lapses with the token it hangs off.
- Regenerate the token to move it. The same string is copied into the
  new zone, and it has to answer from both authorities across the
  switch.
- Change the delegation before the new zone answers, or while a DS
  record still names the old nameservers' keys.
- Delete and recreate a zone to change it. The nameservers change with
  it, the registrar does not follow, and each fresh zone draws from a
  finite per-domain pool.

**SHOULD:**

- Move the apex once rather than delegating a subdomain per
  environment, so the registrar is a one-time act.
- Set a CAA record naming the issuing CA.

## References

- [cloud-account](cloud-account.md) — domain verification, and why the
  directory work has no API.
- [cloud-naming](cloud-naming.md) — the `dz-` prefix and the
  environment letter.
- [queenswood-installation](queenswood-installation.md) — the manifest
  the domain is named in.
