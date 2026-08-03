<script>
  /* PanelHead — a Panel's title bar: title, an optional muted count or
     qualifier beside it, and a right-hand `note` or `actions` snippet.

         <PanelHead title="Runs" count={2} note="one run per business day" />

         <PanelHead title="Preview" count="dry run — nothing moves">
           {#snippet actions()}
             <SearchField bind:value={q} size="sm" />
           {/snippet}
         </PanelHead>

     `count` is deliberately untyped — a number reads as a count, a
     string as a qualifier, and both sit in the same muted slot. */

  let { title, count, note, actions, children } = $props();
</script>

<header class="qw-panel-head">
  <h3 class="qw-panel-title">
    {title}
    {#if count !== undefined && count !== null}
      <span class="qw-panel-count">{count}</span>
    {/if}
    {@render children?.()}
  </h3>
  {#if actions}
    <div class="qw-panel-actions">{@render actions()}</div>
  {:else if note}
    <span class="qw-panel-note">{note}</span>
  {/if}
</header>

<style>
  .qw-panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
    padding: 14px 20px;
    border-bottom: 1px solid var(--rule-2);
  }
  .qw-panel-title {
    margin: 0;
    display: flex;
    align-items: baseline;
    gap: 10px;
    font-family: var(--grotesk);
    font-size: 15px;
    font-weight: 500;
    color: var(--fg);
  }
  .qw-panel-count {
    font-family: var(--mono);
    font-size: 11px;
    font-weight: 400;
    letter-spacing: 0.02em;
    color: var(--fg-muted);
  }
  .qw-panel-note {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
  }
  .qw-panel-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
</style>
