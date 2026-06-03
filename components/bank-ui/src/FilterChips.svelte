<script>
  /* FilterChips — renders a capability's or limit's filters.

     Both capabilities and limits carry an optional `filters` list. Each
     filter is a small `{ key, value }` pair flattened from the proto
     filter messages (see the bank-console policy-adapter):

       BalanceLimitFilter { computed{ name:"available" }, transaction_type:"transfer" }
         → [ { key:"computed", value:"available" }, { key:"txn", value:"transfer" } ]

     An empty list means the capability/limit is UNSCOPED — it applies to
     anything in that domain — and renders as a muted "any". When there
     are more than `max`, the overflow collapses into a "+N more" chip
     whose title lists the rest. */

  let { filters = [], max = 3 } = $props();

  const shown = $derived(filters.slice(0, max));
  const extra = $derived(Math.max(0, filters.length - max));
  const overflowTitle = $derived(
    filters.slice(max).map((f) => `${f.key}: ${f.value}`).join(", ")
  );
</script>

{#if filters.length === 0}
  <span class="qw-fchip qw-fchip--any">any</span>
{:else}
  <span class="qw-filter-chips">
    {#each shown as f}
      <span class="qw-fchip"><span class="fk">{f.key}</span> {f.value}</span>
    {/each}
    {#if extra}
      <span class="qw-fchip qw-fchip--more" title={overflowTitle}>+{extra} more</span>
    {/if}
  </span>
{/if}

<style>
  .qw-filter-chips {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 5px;
    align-items: center;
  }
  .qw-fchip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 20px;
    padding: 0 7px;
    border-radius: 5px;
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-2);
    background: var(--surface-sunk);
    border: 1px solid var(--rule-2);
    white-space: nowrap;
  }
  .qw-fchip .fk { color: var(--fg-muted); }
  .qw-fchip--any {
    color: var(--fg-muted);
    background: transparent;
    border-color: transparent;
    padding: 0;
    height: auto;
  }
  .qw-fchip--more { color: var(--fg-muted); cursor: default; }
</style>
