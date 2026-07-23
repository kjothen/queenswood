<script>
  /* Phase — monospace pill for a balance phase.

     A balance is keyed by (address, phase, asset). The phase pill marks
     whether the balance is settled or still moving:

       posted        settled            (pine — same family as Badge "published")
       pending_in    awaiting credit    (violet)
       pending_out   awaiting debit     (violet)

     Distinct from <Badge> on purpose: Badge is a rounded status pill for
     records (draft / published / …); Phase is a squared mono chip for a
     ledger dimension. Different shape = different meaning at a glance. */

  let { phase = "posted" } = $props();
  const tone = $derived(phase === "posted" ? "posted" : "pending");
</script>

<span class="qw-phase qw-phase--{tone}">{phase}</span>

<style>
  .qw-phase {
    display: inline-flex;
    align-items: center;
    height: 18px;
    padding: 0 7px;
    border-radius: 4px;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.03em;
    text-transform: lowercase;
    white-space: nowrap;
  }
  .qw-phase--posted {
    background: light-dark(oklch(0.92 0.04 145),  oklch(0.26 0.05 145));
    color:      light-dark(oklch(0.34 0.075 145), oklch(0.82 0.06 145));
  }
  .qw-phase--pending {
    background: light-dark(oklch(0.92 0.04 270),  oklch(0.27 0.05 270));
    color:      light-dark(oklch(0.40 0.08 270),  oklch(0.80 0.07 270));
  }
</style>
