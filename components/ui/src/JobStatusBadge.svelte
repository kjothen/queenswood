<script>
  /* JobStatusBadge — maps a job's run state to the matching Badge tone.

     `outcome` is one of succeeded | failed | running | scheduled (see
     lastOutcome() in jobs.js). The tone word doubles as the label, so
     the badge reads "succeeded" / "failed" / "running" / "scheduled". */

  import Badge from "./Badge.svelte";

  let { outcome = "scheduled" } = $props();
</script>

<Badge tone={outcome}>{outcome}</Badge>
