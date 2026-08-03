<script>
  /* MigrationStatusBadge — maps a migration, run or per-account outcome
     to the matching Badge tone, mirroring AccountStatusBadge.

       kind="migration"  draft | approved | completed | cancelled
       kind="run"        running | completed | failed
       kind="outcome"    eligible | migrated | ineligible | failed */

  import Badge from "./Badge.svelte";
  import {
    MIGRATION_TONE,
    OUTCOME_TONE,
    RUN_TONE,
    shortEnum,
  } from "./migrations.js";

  let { status, kind = "migration" } = $props();

  const TONES = { migration: MIGRATION_TONE, run: RUN_TONE, outcome: OUTCOME_TONE };

  const label = $derived(shortEnum(status));
  const tone = $derived(TONES[kind]?.[label] ?? "neutral");
</script>

<Badge {tone}>{label}</Badge>
