# Moving a domain's delegation

<!-- tessl-plugin: deployment -->

## Status

**Verified.** One domain was moved this way.

## Problem

You have a zone the plane composed, and a domain still answered for by
the registrar that sold it.

## Solution

### Prerequisites

- A public zone, composed and answering — that is
  [queenswood-installation](queenswood-installation.md), and this
  recipe cannot begin without it, because every step below queries the
  nameservers Cloud DNS assigned it.
- The domain prepared by [gcp-dns](gcp-dns.md): verified, its
  automation identity an owner, inventoried, and unsigned if it was
  signed.
- An account at the registrar that can change nameservers.

```bash
just queenswood-zone-nameservers
```

The four the zone answers on, which every step below aims at.

### 1. Diff the two authorities

Query the new zone's own nameservers directly. A public resolver still
answers from the old authority, so it cannot tell you anything about
the new one.

```bash
diff <(just dns-records <domain> <assigned-nameserver>) \
     <(just dns-records <domain> <registrar-nameserver>)
```

The same sweep the domain was inventoried with, asked of each
authority in turn. Everything
that carries should answer identically; what differs is the SOA, the NS
records themselves, and whatever was decided not to bring over.

Done in this order the propagation window is a no-op, because there is
no interval in which the two authorities disagree.

### 2. Move the delegation

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

### 3. Check

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
entire point of step 1.

Last, confirm the verification TXT answers from the new authority. The
organisation rests on that record, and it is the one whose absence does
not show up as a broken page.

Set a CAA record naming the issuing CA once it is known. An absent CAA
authorises every CA rather than none.

## Rules

**MUST:**

- Diff the sweep from each authority before delegating, with
  `just dns-records <domain> <nameserver>` aimed at each.
- Query the registry's authority section to check the delegation
  itself, since a referral carries the NS records there rather than in
  the answer.
- Confirm the verification TXT resolves from the new authority
  afterwards.

**MUST NOT:**

- Change the delegation before the new zone answers, or while a DS
  record still names the old nameservers' keys.
- Replace only some of the registrar's nameservers.
- Delete the old records at the registrar as part of the move. They are
  the way back.
- Re-enable DNSSEC at the registrar after moving.

**SHOULD:**

- Set a CAA record naming the issuing CA.

## Discussion

The delegation moves last because a delegation to a zone that does not
answer is an outage, and because the token proving ownership has to be
answering from the new zone before the move — or the move takes
ownership away with it, which is the failure that takes the zone too.

Done in the order above the propagation window is a no-op: both
authorities answer the same, so there is no interval in which resolvers
holding either one are wrong.

## References

- [gcp-dns](gcp-dns.md) — preparing the domain, which comes first.
- [queenswood-installation](queenswood-installation.md) — composing the
  zone this delegates to.
