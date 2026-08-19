# Cloud DNS
<!-- tessl-plugin: deployment -->

## Problem

The zone and the records in it are composed, but the things around them
are not. A public zone cannot be created by an identity that has not
proved it owns the domain, the delegation that sends the internet to
that zone is edited wherever the domain was bought, and a signed domain
has to be unsigned before it can move. None of that has an API the
plane can reach.

This is that half: what a person does, in what order, and what each
step is waiting on. Where the zone lives and what composes the records
in it belongs to the composition.

## Solution

### Before you start

- The domain, and an account that can edit its DNS and change its
  nameservers at the registrar.
- A Google account for the installation — the operator account, not a
  personal one.
- The email address of the identity the composition runs as.
- The management project, which is where the zone goes.

### The order, and why it is this order

Three things gate each other, and getting them the wrong way round
costs hours rather than minutes.

Ownership comes first, because a public zone cannot be created without
it. The zone comes before the delegation, because a delegation to a
zone that does not answer is an outage. And the token that proves
ownership has to be answering from the new zone *before* the delegation
moves, or the move takes ownership away with it — which is the failure
that takes the zone with it too.

Unsigning is the long pole and the one that hurts. Read step 4 before
choosing where to put it: it wants to be early, and there is one case
where it should be last.

### 1. Verify the domain

Cloud DNS refuses to create a public zone whose name the calling
identity has not verified, and reports
`verifyManagedZoneDnsNameOwnership` when it does. The check is against
Google Site Verification, not against anything in Cloud DNS — Cloud DNS
only consults it.

Signed in as the operator account, at `search.google.com/search-console`:

1. Add a property, of type **Domain** rather than URL prefix. A Domain
   property covers every subdomain, so no environment ever needs
   verifying separately.
2. Copy the `google-site-verification=` token it issues.
3. At the registrar, add it as a TXT record at the apex. A registrar
   with a preset menu usually has an entry for this — Squarespace calls
   it Google Workspace Verification — and the preset is only a
   convenience for pasting the value.
4. Back in Search Console, verify.

Then check **Settings, then Ownership verification** and confirm the
method it recorded. Google may auto-verify by recognising who provides
the domain's DNS and record the method as `Domain name provider`, in
which case your token is sitting in DNS unused — and that verification
rests on a provider relationship step 7 ends. Add the DNS TXT method
explicitly so ownership rests on a record you control.

Three things about verification are easy to get wrong.

**It belongs to an identity, not to a domain.** A token verifies
whoever placed it, so a `google-site-verification` record already at the
apex is no evidence your account owns anything. An empty property list
is no evidence of the reverse either: Search Console lists properties
somebody added, and the verification Cloud Identity performs at signup
is administered from the Admin console and creates no property at all.
A domain can be verified, primary, and absent from Search Console
entirely. See [cloud-account](cloud-account.md).

**Delegated ownership lapses with the token it hangs off.** An owner may
add owners, so any verified account unblocks the rest — but an
installation whose identities all delegate from somebody's personal
account loses the domain when that account is tidied up. Have the
operator account verify in its own right. A domain carries several
verification records without conflict, so this is an addition rather
than a migration, and an unattributed token already present stays:
nothing reports which record holds the organisation's own domain.

**Neither neighbouring permission system is this one.** The Admin
console manages Cloud Identity's domains. The registrar has an account
model of its own, granting people the right to edit DNS and move the
registration. Neither grants ownership in Google's sense.

### 2. Grant the composition's identity ownership

The plane authenticates through Workload Identity, so the provider
creates the zone as the platform service account rather than as you,
and a service account is never a verified owner by default.

**Settings, then Users and permissions, then Manage property owners.**
The Add an owner box takes a service account address like any other.
Choose **Owner** — Full and Restricted grant report access and confer no
ownership, and a zone create refuses them exactly as it refuses a
stranger.

Where that is not wanted, create the zone by hand as a verified human
and let the composite adopt it.

### 3. Inventory the registrar

Find out what the domain is actually serving. The records that matter
are rarely the ones it is visibly for.

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

Then decide record by record:

- **Carries.** Verification tokens, including any you cannot attribute.
  SPF, whose `-all` states the domain sends no mail, and DMARC, which is
  the half that makes it enforceable.
- **Does not carry.** A placeholder site's A records and `www` CNAME,
  which describe a page nobody depends on. `_domainconnect`, which names
  the old provider's one-click DNS endpoint and means nothing once the
  domain delegates elsewhere.

Whatever carries goes into the manifest the zone is composed from.

### 4. Unsign, if the domain is signed

```
dig +short DS <domain>
```

An answer means DNSSEC is on, and the domain cannot move until it is
off. The DS record lives at the parent registry and names the keys the
current nameservers sign with. Changing the delegation does not touch
it, so the new authority answers unsigned against a parent still
asserting signatures — and validating resolvers treat that as forgery
rather than as an absence. Not a stale answer for a while: SERVFAIL
everywhere at once, the verification TXT included, and with it the
ownership the zone rests on.

The mismatch fails closed deliberately, since the alternative would let
an attacker strip signatures to downgrade a protected domain. It is
also the one failure the diff in step 6 cannot protect against: that
makes the two authorities agree on content, and the parent's assertion
is not about content.

Keeping it signed across the move is not available. It would need the
new provider's keys pre-published in the old zone, and Cloud DNS
generates and owns its keys rather than accepting an existing one.

**Expect the outage to begin here, not at the delegation.** A registrar
may strip the zone's keys at once and submit the DS withdrawal to the
registry afterwards, leaving the domain in exactly the mismatched state
above for as long as that takes. So:

- **For a domain not yet serving anything, do this first**, before
  step 1 even, and let the wait overlap everything else.
- **For a domain serving traffic, do it last**, immediately before
  step 7, and accept a serial wait in exchange for the tightest
  possible window.

Watch the parent, not the zone:

```
dig +short DS <domain> @<a registry nameserver, e.g. a0.nic.io>
```

Empty is the signal. Then wait out the DS record's own TTL — the
registry's, not the zone's — because a resolver still holding a cached
DS goes on expecting signatures after the registry has dropped it.
Recovery is uneven while that happens: public resolvers are anycast,
each node caches independently, and one probe hits one node, so a
single clean answer proves nothing. Take several, spaced, across more
than one resolver.

### 5. Let the plane compose the zone

Merge the manifest naming the domain and the records from step 3. The
zone and its records compose together, so this covers both the create
and the populate. Then read the nameservers Cloud DNS assigned it —
four, unpredictable, and not the same for two zones:

```
kubectl --context <plane> -n crossplane-system \
  get managedzone <zone> -o jsonpath='{.status.atProvider.nameServers}'
```

### 6. Diff the two authorities

Query the new zone's own nameservers directly. A public resolver still
answers from the old authority, so it cannot tell you anything about
the new one.

```
dig @<assigned-nameserver> <domain> TXT +short
dig @<assigned-nameserver> _dmarc.<domain> TXT +short
```

Compare against the same queries aimed at the registrar's nameservers.
Everything that carries should answer identically. Done in this order
the propagation window is a no-op, because there is no interval in
which the two authorities disagree.

### 7. Move the delegation

At the registrar, choose custom nameservers and replace **all four**
with the assigned ones, without trailing dots. A mixture of the two
providers' nameservers leaves resolvers getting different answers
depending on which they ask.

The registrar will warn that this reduces functionality or disconnects
the site. That is accurate and intended: its DNS stops being
authoritative, and everything in its DNS panel stops applying.

**Leave the old records in the registrar's panel.** They cost nothing
dormant, and they are what makes reverting a matter of switching the
nameservers back.

**Do not re-enable DNSSEC there.** If signing is wanted again it goes on
the new zone, with a fresh DS published at the registrar afterwards.

### 8. Check

The registrar saving is not the delegation moving. The registrar
submits the change to the registry, which is its own hop and its own
wait. Ask the registry, and ask for the authority section — a referral
carries the NS records there rather than in the answer, so a `+short`
query looks empty and reads as failure:

```
dig NS <domain> @<a registry nameserver> +noall +authority
```

Two TTLs then govern the tail. The registry's delegation TTL is one,
and the NS records inside the old zone are the other, usually much
longer. Resolvers holding the latter keep using the old nameservers
after the parent has changed, so expect a period where resolvers
disagree about who is authoritative. Both answer the same, which is the
entire point of step 6.

Last, confirm the verification TXT answers from the new authority. The
organisation rests on that record, and it is the one whose absence does
not show up as a broken page.

Set a CAA record naming the issuing CA once it is known. An absent CAA
authorises every CA rather than none.

## Rules

**MUST:**

- Verify the domain before a public zone is created, as the operator
  account in its own right.
- Add the property as a Domain property, not a URL prefix.
- Add the composition's identity as an **Owner** of the property. Full
  and Restricted confer no ownership.
- Add the DNS TXT verification method explicitly where the domain was
  auto-verified through its provider.
- Inventory every record type at the registrar, and the
  underscore-prefixed names, before moving a domain.
- Carry the verification tokens, SPF and DMARC into the new zone before
  the delegation moves.
- Check for a DS record before delegating, and where one exists unsign
  at the registrar and wait out the DS TTL first, watching the parent
  registry rather than the zone.
- Take several spaced probes across more than one resolver before
  calling DNSSEC recovery complete.
- Query the assigned nameservers directly to check the new zone, and
  the registry's authority section to check the delegation.
- Confirm the verification TXT resolves from the new authority
  afterwards.

**MUST NOT:**

- Read an existing `google-site-verification` record as evidence your
  account owns the domain, or an absent Search Console property as
  evidence it is unverified.
- Tidy away an unattributed verification token.
- Regenerate a token to move it. The same string is copied, and answers
  from both authorities across the switch.
- Leave the installation's identities delegated from a personal
  account.
- Change the delegation before the new zone answers, or while a DS
  record still names the old nameservers' keys.
- Replace only some of the registrar's nameservers.
- Delete the old records at the registrar as part of the move. They are
  the way back.
- Re-enable DNSSEC at the registrar after moving.
- Delete and recreate a zone to change it. The nameservers change with
  it, the registrar does not follow, and each fresh zone draws from a
  finite per-domain pool.

**SHOULD:**

- Unsign first for a domain not yet serving anything, so the wait
  overlaps everything else; unsign last for one serving traffic, to
  keep the window tight.
- Move the apex once rather than delegating a subdomain per
  environment, so the registrar is a one-time act.
- Set a CAA record naming the issuing CA.

## References

- [cloud-account](cloud-account.md) — domain verification at signup, and
  why the directory work has no API.
- [cloud-naming](cloud-naming.md) — the `dz-` prefix and the
  environment letter.
- [crossplane](crossplane.md) — what composes the zone and its records.
- [queenswood-installation](queenswood-installation.md) — the manifest
  the domain is named in.
