# Data recovery

## Status

This is not a compliance attestation. It states what is required, how
Queenswood meets it, and — plainly, in the same list — where it does
not. A document claiming coverage it does not have is worse than no
document.

It carries no plugin label. An obligation is not a rule for writing
code; the operational rules derived from these obligations live in the
recipes linked below, and those recipes state no regulation.

## What this covers

The obligation to recover Queenswood's data after loss, corruption or
an outage: what the regimes an installation is subject to require, and
which recipe addresses each requirement.

## The regimes

- **DORA** — Regulation (EU) 2022/2554. Article 11 requires ICT
  response and recovery plans. Article 12 covers backup policies and
  procedures, restoration and recovery procedures and methods, and is
  the article most of this maps to. It binds EU financial entities; for
  an entity regulated in the UK the counterpart is the PRA and FCA
  operational resilience regime, which asks the same questions through
  important business services and impact tolerances.
- **GDPR** — Article 32(1)(c): the ability to restore availability and
  access to personal data in a timely manner after a physical or
  technical incident. Binds anyone processing personal data, whatever
  the financial regime says.
- **NIS2** — Article 21(2)(c): backup management and disaster recovery,
  named among the required risk-management measures for essential and
  important entities.
- **CIS Critical Security Controls** — Control 11, Data Recovery,
  safeguards 11.1 to 11.5. Not law, and the shortest checklist the
  others can be assessed through.
- **ISO 22301** and **ISO/IEC 27031** — the business continuity
  management system, and the ICT readiness half, revised in 2025 for
  the first time since 2011. They supply the vocabulary everything else
  here uses: RTO, RPO, MBCO. ISO/IEC 27002 control 8.13 is information
  backup.

## What is required, and how it is met

The regimes overlap heavily, so each requirement appears once with
every regime that states it.

### A documented recovery procedure

**Required by** DORA 12(1), CIS 11.1, ISO/IEC 27031.

**Met.**

- **FoundationDB** — [fdb-recovery](../recipes/fdb-recovery.md): the
  recovery scenarios, which restore mode each takes, and the measured
  RPO of each.
- **Keycloak** —
  [recovery-procedures](../recipes/recovery-procedures.md), whose
  FoundationDB half is superseded and whose Keycloak half is not.

### Automated backups, at a frequency set by criticality

**Required by** DORA 12(1), CIS 11.2, ISO/IEC 27002 8.13.

**Met in mechanism, undeclared in policy.**

- **FoundationDB** — full snapshots hourly, with the mutation log
  shipped continuously between them, which is what gives point-in-time
  recovery across the restorable window rather than only at snapshots.
- **Keycloak** — both realms exported hourly, with `LATEST` written
  only once the realms and the manifest are all present.

No document states the frequency each store's criticality justifies, so
the running configuration is the only record of a decision nobody wrote
down.

### Recovery data protected equivalently to its source

**Required by** DORA 12(2), CIS 11.3, GDPR 32(1).

**Partially met.**

- **FoundationDB** — met. The backups are encrypted under a key held in
  Secret Manager that has never been in git; see
  [external-secrets](../recipes/external-secrets.md).
- **Keycloak** — not met. The realm exports sit in the same bucket as
  plain JSON carrying user credential records and client secrets, which
  is a weaker control than the source Keycloak applies. Closing it means
  CMEK on the bucket, bringing both kinds of object under one key.

### Recovery data isolated from the system it protects

**Required by** CIS 11.4.

**Met.**

- **The backups bucket** — in the installation's recovery project
  rather than the instance's. The instance writes to it and cannot
  delete it.
- **The encryption key** — composed into the recovery project for the
  same reason. A key held in the project whose data it protects is not
  a second copy of anything.

### Restoration onto segregated systems

**Required by** DORA 12(3): restoring with your own systems must use
ICT systems physically and logically segregated from the source system.

**Not met.**

- **FoundationDB** — the only path the chart supports restores in
  place, over the data being replaced, and it needs the destination
  emptied first, which nothing declares.
- **Keycloak** — no separate-instance path exists either, and the
  cutover it would need is the larger part: DNS, the realm the console
  signs into, and the user ids that FoundationDB records reference.

The segregated shape is the one
[ADR-0026](../adr/0026-recovering-data-and-the-states-that-do-it.md)
prefers on evidence grounds — the damaged data is the only record of
what happened, and restoring over it destroys that record. In-place
restoration remains appropriate for a test environment, which is not
what this provision is about.

### Periodic testing of the restoration procedure

**Required by** DORA 12(1), CIS 11.5, NIS2 21(2)(c), ISO/IEC 27031.

**Not met.**

- **FoundationDB** — no restore has been performed in this
  installation. Verification reaches as far as objects existing and, if
  anyone runs it, metadata decrypting under the key; neither proves the
  data comes back.
- **Keycloak** — likewise. The hourly export began before anything had
  read one back.

CIS 11.5 puts the cadence at least quarterly; DORA requires it
periodically without fixing a number. This is the load-bearing gap:
every other line here describes machinery that has been built and
observed, and this is the reason none of it is yet evidence.

### Declared recovery objectives

**Required by** ISO 22301 and ISO/IEC 27031 as the governing targets,
expected by NIS2, and expressed in the UK regime as impact tolerances
on important business services.

**Not met.**

- **RPO** — [fdb-recovery](../recipes/fdb-recovery.md) states one per
  recovery scenario, but those are measurements of what the running
  configuration achieves rather than targets it is held to.
- **RTO** — neither measured nor declared. No recovery has been timed,
  because none has been performed.

A measurement without a tolerance cannot fail.

### The ability to restore personal data in a timely manner

**Required by** GDPR 32(1)(c).

**Unproven.**

- **Both stores** — this rests entirely on the two requirements above.
  Without a tested procedure and a declared recovery time, "timely" has
  neither a demonstration nor a definition.

## What this installation has not yet declared

These are policy statements rather than procedures, which is why they
belong here and not in a recipe. Each is currently absent:

- **RPO and RTO**, per recovery scenario. The measured figures are in
  [fdb-recovery](../recipes/fdb-recovery.md) and are the natural
  starting point for the targets.
- **The retention period**, in days.
  [ADR-0026](../adr/0026-recovering-data-and-the-states-that-do-it.md)
  establishes that it is one number and that everything else — the
  expiry cutoff and the restorable floor — derives from it. The number
  itself has never been chosen.
- **Backup frequency per store**, justified by that store's
  criticality, rather than inherited from a values file.

## References

- [fdb-recovery](../recipes/fdb-recovery.md) — the FoundationDB
  recovery scenarios and procedures
- [recovery-procedures](../recipes/recovery-procedures.md) — Keycloak
  recovery
- [external-secrets](../recipes/external-secrets.md) — where the backup
  encryption key lives, and why it is never rotated
- [ADR-0026](../adr/0026-recovering-data-and-the-states-that-do-it.md)
  — the recovery position, the two shapes, and retention as one number
- [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — off as a declared state, and why an instance's project is durable
