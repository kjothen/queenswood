# Plan: move the apex out of qw01

## Context

[ADR-0028](../adr/0028-the-apex-belongs-to-no-installation.md) decided
that the apex belongs to no installation and that every serving name is
delegated to the installation that answers on it. The estate does
neither. One zone in the nonprod installation's management project is
the apex, is what the registrar points at, and holds another
installation's serving records as well:

```
dz-qw01-c-queenswood-io            in prj-qw01-c-mgmt-xxxxxx
  queenswood.io.              TXT     two ownership tokens, SPF
  _dmarc.queenswood.io.       TXT     mail policy
  api.test.queenswood.io.     A       the test instance's address
  console.test.queenswood.io. A
  keycloak.test.queenswood.io. A
  _acme-challenge_xxxxxx.test.queenswood.io. CNAME   certificate
```

The first two are the plane's, composed from `installation.yml`'s `dns`
block. The last four are the `test` instance's, composed by its
`XPublicEndpoint`.

The ADR's `In order` is written for an organisation bringing a domain
in for the first time. This is the other case: the apex exists, it is
delegated, and something is serving from it.

## What makes it safe

Two zones for one name can exist at once, and only the delegated one
answers. So the new apex is built, filled and proved *before* the
registrar is touched, which is
[gcp-dns-delegation](../recipes/infra/gcp-dns-delegation.md)'s whole
procedure and its promise that the propagation window is a no-op.

The same trick moves a name under delegation with no window, one level
down: the parent holds the records directly until the child zone
answers, and then a single apply swaps those records for an NS record.
The name never stops resolving from a zone that is already
authoritative — it only changes how that zone answers it.

So the migration copies the `test` records up into the new apex, moves
the registrar, and only then hands them down to a zone of qw01's own.

## Traps

**A record's zone is its identity, and nothing says so.** The managed
zone sits inside a `RecordSet`'s external name, so repointing an
instance's `ingress.zone` leaves the spec naming one resource while
upjet goes on observing another. It finds the fields it knows about
equal and reconciles successfully: `Synced`, `Ready` and
`LastAsyncOperation` all read healthy, and the records stay where they
were. There is no refusal, because nothing is attempted.

`crossplane.io/external-name` against the spec is the only place the
disagreement shows — see
[crossplane-providers](../recipes/infra/crossplane-providers.md). So
the four `rs-qw01-n-test-*` managed resources are deleted and
recomposed rather than edited, and nothing will tell you that is
needed.

**The address carries `Delete`.** Rebuilding the endpoint composite
releases the IP and takes a different one, which makes every record
naming it wrong. So the endpoint is repointed, never rebuilt, and there
is no way back through a rebuild.

**The old zone answers until the registrar moves.** Nothing in
`installation.yml` is touched before then. Removing the `dns` block
early takes the live apex down.

**Deleting the old zone is the only irreversible act here.** It is last
and it is on its own.

## Ordering

**Build the new apex. Nothing is delegated to it yet.**

1. Open the seed and create `prj-c-dns-<suffix>` at the organisation
   with the DNS API enabled.
2. Create the apex zone in it, running as the verified person rather
   than as a service account. This draws its nameserver set once.
3. Write `apex.yml` with the domain, the project, and **every record
   the old zone holds** — the two TXT sets and, temporarily, copies of
   the four `test` records. Apply it.
4. Diff both authorities: the same sweep against the old zone's
   nameservers and against the new zone's. They must agree before
   anything moves.

**Cut the delegation over.**

5. Move the registrar onto the new apex's nameservers, and confirm the
   ownership TXT answers from the new authority. From here the new zone
   serves everything, and nothing has changed for anyone resolving it.

**Give `test` a zone of its own.**

6. Reshape `XPublicZone` to the ADR's spec, commit `test`'s zone
   manifest in the installation's directory, and merge. `dz-qw01-n-test`
   appears, empty, delegated to by nobody.
7. Repoint `test`'s `ingress.zone` at it and delete the four
   `rs-qw01-n-test-*` managed resources so they recompose against the
   new zone. The records appear there; nothing consults them yet.
8. In one apply, remove the four copies from `apex.yml` and add the NS
   record for `test` naming the new zone's nameservers. Resolution
   moves from direct records to a delegation inside a zone that is
   already authoritative.

**Retire what is left.**

9. Remove the `dns` block from `installation.yml`. The plane drops the
   zone and the record objects and both survive in GCP, because neither
   carries `Delete`.
10. Remove the `dns` step and its XRD field from `XManagementPlane`,
    now that no manifest sets it.
11. Delete the old zone, deliberately and on its own.

## The careful path, on an estate that could take the rough one

Nothing outside the estate resolves any of these names, so steps 3 and
8 could be dropped and `test` would simply stop answering between step
5 and step 7. They are done anyway. The copy-then-swap is the general
way to introduce a delegation without a gap, prod will need exactly
that shape for the apex record itself, and the only chance to practise
it is on something whose failure costs nothing.

Which is also what makes the rehearsal honest: it is the same
procedure, not a shortened one, run where being wrong is affordable.

### The way back, and where it stops

Until step 11 the old zone still exists and still holds every record.
So the rollback for anything in steps 1 to 10 is to point the registrar
back at its nameservers — the same act as step 5, reversed, and bounded
by the same propagation. That is a real rollback rather than a hoped-for
one, and it is worth confirming the old zone still answers before
relying on it.

Step 11 is where it expires, which is why it is last and alone.

### The one thing being disposable does not make free

Every fresh zone for a domain draws from a finite per-domain pool of
nameserver sets, and the draw is not returned when the zone is deleted.
So the apex zone is created once and not experimented with: creating
and deleting it a few times to try variations is the one action here
that spends something a rebuild cannot recover. Practise the delegation
and the record moves as much as is useful; do not practise step 2.

### Prod is not migrated at all

It is built under
[ADR-0028](../adr/0028-the-apex-belongs-to-no-installation.md) from the
start, with its own delegated zone from its first day, which is what
makes it never need this plan.

## References

- [ADR-0028](../adr/0028-the-apex-belongs-to-no-installation.md) — what
  the estate is being moved to, and why.
- [gcp-dns](../recipes/infra/gcp-dns.md) — proving ownership, and why a
  zone is expensive to recreate.
- [gcp-dns-delegation](../recipes/infra/gcp-dns-delegation.md) — moving
  a delegation once the new zone answers.
- [crossplane-providers](../recipes/infra/crossplane-providers.md) — a
  ForceNew field is refused, not replaced.
- [crossplane-live](../recipes/infra/crossplane-live.md) — what a
  change to a live resource does.
