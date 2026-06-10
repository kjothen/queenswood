<script>
  /* JobKindChip — a small mono chip marking a job's ownership.

     `user`   — quiet outline chip; the operator owns the schedule.
     `system` — gold-tinted chip with a lock; the platform fixes the
                cadence (only the time of day is editable). */

  let { kind = "user" } = $props();

  const isSystem = $derived(kind === "system");
</script>

<span
  class="kind-chip"
  class:system={isSystem}
  title={isSystem
    ? "System job — cadence fixed by the platform"
    : "User job — operator-editable"}
>
  {#if isSystem}
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <rect x="3.5" y="7" width="9" height="6" rx="1" />
      <path d="M5.5 7 V5.2 A2.5 2.5 0 0 1 10.5 5.2 V7" />
    </svg>
  {/if}
  {kind}
</span>

<style>
  .kind-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 18px;
    padding: 0 7px;
    border-radius: 5px;
    border: 1px solid var(--rule);
    background: transparent;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.02em;
    text-transform: lowercase;
    color: var(--fg-muted);
    white-space: nowrap;
  }
  .kind-chip svg {
    width: 10px;
    height: 10px;
    fill: none;
    stroke: currentColor;
    stroke-width: 1.4;
  }
  .kind-chip.system {
    border-color: light-dark(oklch(0.82 0.08 82), oklch(0.42 0.07 80));
    background: light-dark(oklch(0.95 0.05 84), oklch(0.30 0.05 80));
    color: var(--gold-deep);
  }
</style>
