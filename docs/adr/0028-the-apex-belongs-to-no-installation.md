# 28. The apex belongs to no installation, and every name below it is delegated

<!-- tessl-plugin: deployment -->

## Status

Proposed, and built as far as the repository goes. `XPublicZone` holds
an environment's zone, the `dns-apex-*` recipes declare and apply the
apex from `apex.yml`, `environment.yml` carries the installation's
domain, and the recipes describe all of it.

What has not happened is the estate. The one apex still sits inside the
nonprod installation and the registrar still points at it, so
`XManagementPlane` keeps composing a zone until
[apex-dns-migration](../plan/apex-dns-migration.md) has run.

## Context

The apex is the domain name carrying no subdomain prefix —
`queenswood.io` — and the zone holding it is what a registrar delegates
to. Where a parent organisation hands over `qw01.acme.com`, no apex of
ours is involved: theirs is `acme.com`, and what we are given is a
subdomain of it, which is one more reason the apex is never an
installation's.

One zone holds every name in the estate. It sits in the nonprod
installation's management project, its apex is `queenswood.io`, and it
carries the ownership tokens and mail policy at that apex plus the
records for every environment below it, each written by an instance's
`XPublicEndpoint`.

More than one instance in that installation is already fine, and worth
saying so the limit is not mistaken for a lower one. Every resource an
endpoint composes is named for its instance's code, environment and
label, so two of them collide nowhere, and
[instance-deploy](../recipes/infra/instance-deploy.md) requires each to
state a distinct `ingress.domain`. The zone is created once and every
instance after it is free.

A second *installation* is what breaks it, three ways at once.

A domain has one delegation. The registrar points `queenswood.io` at
one nameserver set, so exactly one zone is authoritative for it. A
second `ManagedZone` with the same `dnsName` in another project is
legal, draws its own nameservers, and answers nobody — both composites
report healthy and one of them is furniture.

Sharing the one zone means the second installation writes into the
first's project. That is cross-folder IAM, against the property
[ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md) is built
on: each folder is independent and identically shaped, with its own
identities, because those rights are folder-scoped.

And the apex has to be able to point somewhere other than the
installation holding it. Normally that is prod; during a disaster it is
whatever is still standing. A zone owned by prod cannot redirect away
from prod when prod is the thing that is broken.

The third reframes the other two. The apex is not prod's front door. It
is the estate's, what it points at is an operational decision, and both
outlive any installation.

## Decision

### The apex belongs to no installation

A project at the organisation, `prj-c-dns-<suffix>`, beside
`prj-b-seed-<suffix>` and outside every folder, holding the public zone
for the apex domain. It carries no code because it belongs to no
installation, which the seed project is already the precedent for.

It is created during the organisation's foundation and not during any
installation's, by a person holding two organisation capabilities:
`projectAdmin` to create a project above every folder, and
`billingAdmin` to bill it. Nothing installation-scoped is involved, so
nothing below the organisation has to exist first.

Where a folder is handed over by somebody else's, none of this is
built. The apex is theirs, the installation is a delegate, and nothing
moves into the folder — an apex inside a folder would be an apex owned
by an installation, which is the thing this rejects. `prj-c-dns` is not
the general case; it is the degenerate one where nobody is above us.

### It is declared in git, and applied by a person

The apex is declared, and nothing reconciles the declaration. Those are
separate properties and only the second is being given up.

Its contents live in the manifests repository, at the root beside the
installation directories, since they belong to the organisation and to
no installation:

```yaml
# apex.yml
domain: "queenswood.io"
projectId: "prj-c-dns-xxxxxx"
records:
  - type: TXT
    rrdatas: ['"google-site-verification=..."', '"v=spf1 -all"']
  - name: "_dmarc"
    type: TXT
    rrdatas: ['"v=DMARC1; p=reject; sp=reject; adkim=s; aspf=s"']
  - name: "test"
    type: NS
    rrdatas: ["ns-cloud-a1.googledomains.com.", "..."]
```

`just dns-apex-diff` reports what the zone holds against what the file
says, and `just dns-apex-apply` reconciles it. Neither creates or
deletes a zone: the zone is made once, deliberately, by a separate
recipe that refuses where one already exists, because creating one is
the act that draws a nameserver set and cannot be taken back.

So the estate's shape — which installation answers on which name — is
in git, reviewed in a pull request, and diffable against reality. What
is absent is only the standing agent: no controller holds authority to
act on the one zone that cannot be rebuilt, at a moment nobody chose.
A record a person edits during a failover is the last thing that should
have a reconciler racing to revert it, and a delegation added to the
file is a change somebody reads before it happens.

The file is deliberately shaped like the XR it declines to be. It is
`XPublicZone`'s spec, so if this is ever wrong, what changes is who
applies it and nothing else.

This amends [ADR-0025](0025-building-blocks-and-what-cannot-be-one.md),
which drew the line at what has no API at all. Cloud DNS has one. The
line is now no API, **or** nothing that should act unattended — and it
needs saying out loud, or the absent controller reads as an oversight
and somebody helpfully supplies one.

Applying it is an organisation-scoped capability, declared in
[organisation-roles.json](/infra/access/organisation-roles.json) with
the rest of what is bound above a folder rather than inside one.

### Every serving name is delegated, and belongs to one installation

Names stay flat. Each instance answers on its own subdomain of the apex
— `dev.`, `test.`, `prod.`, `dr.` — and each of those is a delegation
from the apex to a zone the owning installation holds in its own
management project.

An installation composes one `XPublicZone` per instance domain, named
`dz-<code>-<env>-<label>` with the domain in the spec rather than in
the name. It composes a `ManagedZone` and nothing else: the records at
the apex are the apex's, and everything below is the instance's.

An instance's `XPublicEndpoint` does not change. `subdomains` already
composes one A record per entry, and those plus the certificate's
validation CNAME all write into `ingress.zone`. Pointing that at the
installation's own zone instead of at a shared apex is two values in
one manifest.

### An installation writes nothing above itself

The delegation is one-directional. The apex names the installation's
nameservers; the installation names nothing. No installation holds IAM
in the apex project, or a reference to it, or its name.

What crosses is one act by a person per delegated name: read the new
zone's four nameservers with `just queenswood-zone-nameservers`, commit
them to `apex.yml` as an NS record, and apply. For a subdomain the
parent zone is the registrar, so this is
[gcp-dns-delegation](../recipes/infra/gcp-dns-delegation.md) aimed one
level up rather than at whoever sold the name — and unlike a registrar,
the parent is a file, so the delegation is proposed, reviewed and
merged before it exists.

It gates the certificate. A `DNSAuthorization`'s validation CNAME has
to resolve publicly before the certificate issues, and it cannot until
the NS record exists — so the zone is composed and delegated *before*
the instance is deployed. Otherwise an instance stands up reporting
healthy with a certificate pending on an act nobody has been told to
perform.

Per installation, once: its platform identity is added as an Owner on a
Search Console **Domain** property for the highest name that
installation controls — the apex where the apex is ours, the delegated
subdomain where it is not. Cloud DNS refuses a zone create by an
identity that is not a verified owner of the name and reports
`verifyManagedZoneDnsNameOwnership`; a Domain property covers
everything below it, so one grant covers every zone that installation
will ever compose.

This is not a new act. It is
[gcp-dns](../recipes/infra/gcp-dns.md)'s step 2, unchanged, performed
once per installation rather than once per estate — and the identity is
the installation's platform one, since a service account is never a
verified owner by default.

Scoping by what the installation controls is what makes a handed-over
domain answerable. Ownership of `acme.com` granted to our service
account is a refusal waiting to happen; ownership of `qw01.acme.com` is
a TXT record and grants nothing above it.

That TXT has to be in the parent's zone before the NS delegation and in
ours after it, because a delegated name stops being served by the
parent for anything but NS and DS. Verify, compose the zone, carry the
same token into it, then delegate.

### The contract states a domain, and sometimes a zone

What an installation is given goes in `environment.yml`, beside the
folder and the access mapping, and takes the same shape the folder
already does: one block, two modes, discriminated by which field is
set.

```yaml
dns:
  domain: "qw01.acme.com"
  zone:
    name: "dz-acme-shared"
    project: "prj-acme-dns-xxxxxx"
```

`domain` alone is the delegated case, and the ordinary one. The
installation composes a zone per instance domain under it and waits for
an NS record above. Nothing about the parent appears here, because
nothing about the parent is needed: owning the apex ourselves and being
delegated a subdomain of somebody else's are the same case from this
side, which is what makes the design portable rather than merely
tolerant.

`zone` names a zone somebody else owns and has granted us rights on. No
zone is composed and records go into theirs. It exists only where the
parent runs Cloud DNS — nothing composes a `RecordSet` into Cloudflare
or Fastly — and it needs IAM in their project, which the delegated case
does not. So it is the fallback rather than the equal option, and the
thing to ask for is always the delegation.

Absent, the installation answers on no name, which is a valid state and
what one stood up before its domain is delegated has.

A parent may also offer to hold the records themselves, taking values
from us. That is not a third mode and is not supported: it is one
validation CNAME and one A record per subdomain, per instance, entered
by hand — four for a default instance, recurring for every instance
after it, because each composes its own address and no wildcard
collapses them.

Either mode ends `ingress.zone` in a unit manifest. The zone is
derivable — the instance's own from its code, environment and label, or
the environment's adopted one — so a unit states its domain and nothing
else, and stops carrying a second place for the answer to be wrong.

What a parent has to provide beyond this — the folder, an identity able
to create projects in it, a billing account, and the organisation
policies that have to be in force before the first project exists — is
[contract-install](../recipes/infra/contract-install.md)'s, not this.

### The apex points at a front door, eventually

The apex cannot be a CNAME — a name carrying SOA and NS cannot carry
one — and Cloud DNS has no ALIAS. So it is an A record with a literal
address, and the only question is what is on the other end.

Start with prod's own reserved address, switched during a failover by
editing `apex.yml`, and set that record's TTL to 60 seconds from the
first day: a long TTL is only ever discovered to be a mistake during
the incident.

Where that is not fast or certain enough, the same record points
instead at a front door that never changes, and the failover happens
behind it. A global external load balancer with internet NEGs naming
`prod.` and `dr.` as origins does this and crosses projects and folders
without Shared VPC; an edge provider does it too, and additionally
survives GCP being the thing that is broken. Either is org-level, like
the zone. Both are the same apex record, so this is not a decision that
has to be made now — only one that must not be foreclosed.

Every instance keeps a directly addressable name whichever is chosen. A
standby whose only route is through the front door cannot be proved
before it is needed.

### In order

Three sequences, run at three different times, each leaving what the
next one reads.

**Once for the organisation, when the domain is first brought in.**

1. Verify the domain in Search Console as a Domain property, as a
   person, with a TXT at the registrar —
   [gcp-dns](../recipes/infra/gcp-dns.md) step 1.
2. Create `prj-c-dns-<suffix>` at the organisation, outside every
   folder, with the DNS API enabled and `dnsAdmin` bound on it.
3. Create the apex zone in that project, running as the verified
   person rather than as any service account — Cloud DNS refuses a
   create by an identity that does not own the name, and the apex is
   the one name no service account is made an owner of. A recipe, not a
   console: `just gcp-dns-zone-create` is the existing shape, and it
   already opens Search Console where the operator turns out not to be
   verified. It draws the nameserver set once and permanently, so the
   recipe refuses rather than creating a second.
4. Write `apex.yml` — the domain, the project, the verification token,
   SPF and DMARC — and apply it. No delegations yet, because nothing is
   delegated.
5. Move the registrar's delegation onto the zone's nameservers, then
   confirm the verification TXT answers from the new authority before
   trusting it. See
   [gcp-dns-delegation](../recipes/infra/gcp-dns-delegation.md).

**Once per installation, after its plane is built.**

7. Settle the installation's domain: a name below the apex where the
   apex is ours, or whatever a parent hands over.
8. Add the installation's platform identity as an Owner on the Search
   Console property for the highest name it controls. `just
   plane-identity` prints the address, and this cannot come earlier —
   the identity is composed by the plane.
9. State `dns.domain` in `environment.yml`, with `dns.zone` beside it
   where a parent hands over a zone rather than a delegation, and
   merge.

Where the domain was handed over, the parent adds the verification TXT
before step 8 and the NS delegation once the first zone exists. That is
one delegation covering every instance beneath it, and steps 11 and 12
below are then theirs rather than ours.

**Per instance, before it is deployed.**

10. Commit the instance's `XPublicZone` in the installation's directory
    and merge it. Let it reconcile.
11. Read the four nameservers it was assigned, with `just
    queenswood-zone-nameservers`.
12. Commit those to `apex.yml` as an NS record for that name, and
    apply.
13. Confirm the name resolves from the apex before going further. A
    certificate whose validation record cannot resolve stays pending,
    and the instance reports healthy while it does.
14. Deploy the instance —
    [instance-deploy](../recipes/infra/instance-deploy.md).

Prod's apex record is the same shape and comes last. Once its endpoint
holds an address, that address is committed to `apex.yml` as the apex A
record: until a front door exists it is what the apex answers with, and
it is what a failover edits.

## Consequences

**Easier.** Installations become identical. Prod is not special, holds
nothing on behalf of anyone else, and is built and rebuilt like any
other. Adding one is never a change to another, and losing one — or
rebuilding it deliberately — reaches nothing above it. The apex
survives every plane, project and folder in the estate, because it is
in none of them.

An installation in a folder somebody else handed over is the same
installation, asking for a subdomain and an NS record rather than for
rights in anyone's project. And a unit's `ingress.zone` stops being a
choice: it is rendered from the environment's own code, environment and
label, so the two composites that must spell one name cannot disagree.
Deriving it away entirely is available later and costs nothing to
defer.

**Harder.** Flat names make every instance its own delegation, and that
is a cost this adds rather than one it inherits: today an installation
is delegated once and each instance after it is free, where here each
is a zone of its own plus an NS record in a zone the installation
cannot write. Nesting instances under one delegated name per
installation would have avoided it, and was rejected for the names.

The cost falls only where the apex is ours. An installation handed
`qw01.acme.com` gets one delegation and nests everything beneath it,
because one subdomain is what a parent hands over — so the portable
case is the cheaper one, and the expensive one is the estate we own.

Nothing detects a delegation that is missing, stale or wrong, either.
The symptom is a certificate that never issues, which reads as a
certificate problem.
The apex is declared but not reconciled, so a record edited directly in
the console stays that way until somebody runs the diff. The estate
already accepts that for the registrar and the ownership proof; this
widens the surface, and `just dns-apex-diff` is the only thing that
narrows it.

**Deferred.** The front door. Whether the apex's declaration ever gains
a reconciler as well — the file is already the right shape for one, so
that decision costs nothing to defer and nothing to reverse. And the
project's name: `prj-c-dns-<suffix>` says what is in it today, and a
front door beside the zone would make it wrong.

## References

- [ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md) — the
  folder as an installation, and why each is independent.
- [ADR-0023](0023-installation-naming-and-access.md) — the code, the
  environment letter, and the seed project carrying neither.
- [ADR-0024](0024-instances-are-their-own-composites.md) — a composite
  is a unit of replacement.
- [ADR-0025](0025-building-blocks-and-what-cannot-be-one.md) — the
  boundary this amends.
- [gcp-dns](../recipes/infra/gcp-dns.md) — proving ownership, and why
  a zone is expensive to recreate.
- [gcp-dns-delegation](../recipes/infra/gcp-dns-delegation.md) — moving
  a delegation, which a subdomain does against the parent zone.
- [organisation-foundation](../recipes/infra/organisation-foundation.md)
  — the capabilities this takes, and the groups that answer them.
- [contract-install](../recipes/infra/contract-install.md) — the rest
  of what a parent provides, and where `environment.yml` is rendered.
- [ADR-0027](0027-the-folder-is-a-subsidiary.md) — one block, two
  modes, which the folder does first.
