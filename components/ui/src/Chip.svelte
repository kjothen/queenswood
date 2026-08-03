<script>
  /* Chip — a pressable filter pill with an optional trailing count.

         <Chip pressed={filter === "all"} count={9588}
               onclick={() => filter = "all"}>All</Chip>

     Pressed state is published as `aria-pressed`, so a screen reader
     hears which facet is active without relying on the wash.

     Distinct from FilterChips, which renders a read-only list of a
     policy's scope filters and toggles nothing. */

  let { pressed = false, count, disabled = false, onclick, children } = $props();

  const shown = $derived(
    typeof count === "number" ? count.toLocaleString("en-GB") : count,
  );
</script>

<button
  type="button"
  class="qw-chip"
  aria-pressed={pressed}
  {disabled}
  {onclick}
>
  {@render children?.()}
  {#if shown !== undefined && shown !== null}
    <span class="qw-chip-count">{shown}</span>
  {/if}
</button>

<style>
  .qw-chip {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    height: 26px;
    padding: 0 10px;
    border-radius: 999px;
    border: 1px solid var(--rule);
    background: transparent;
    color: var(--fg-muted);
    font-family: var(--grotesk);
    font-size: 12px;
    font-weight: 500;
    line-height: 1;
    white-space: nowrap;
    cursor: pointer;
    transition: background 0.1s, border-color 0.1s, color 0.1s;
  }
  .qw-chip:hover:not(:disabled) {
    background: var(--hover-overlay);
    color: var(--fg);
  }
  .qw-chip:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: 2px;
  }
  .qw-chip:disabled {
    opacity: 0.5;
    cursor: default;
  }
  .qw-chip[aria-pressed="true"] {
    background: light-dark(oklch(0.94 0.025 145), oklch(0.26 0.04 145));
    border-color: light-dark(oklch(0.8 0.05 145), oklch(0.42 0.06 145));
    color: var(--fg);
  }
  .qw-chip-count {
    font-family: var(--mono);
    font-size: 10.5px;
    font-variant-numeric: tabular-nums;
    opacity: 0.7;
  }
</style>
