<script>
  /* CardHeader — kicker + serif title row, with optional actions.

     <CardHeader kicker="ADR-0013" title="One unified API." />
     <CardHeader kicker="..." title="..." titleAs="h2" />
     <CardHeader kicker="..." title="..." actions={someSnippet} />

     Title element defaults to <h3>; override with `titleAs` to fit
     the page's heading hierarchy. */

  let {
    kicker,
    title,
    titleAs = "h3",
    children,
    actions,
  } = $props();
</script>

<header class="card-header">
  <div class="card-header-text">
    {#if kicker}<span class="card-kicker">{kicker}</span>{/if}
    {#if title}
      <svelte:element this={titleAs} class="card-title">{title}</svelte:element>
    {/if}
    {@render children?.()}
  </div>
  {#if actions}
    <div class="card-header-actions">{@render actions()}</div>
  {/if}
</header>

<style>
  .card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }
  .card-header-text {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
  }
  .card-kicker {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--card-kicker-color, var(--gold-deep));
  }
  .card-title {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 22px;
    line-height: 1.15;
    letter-spacing: -0.005em;
    margin: 0;
    color: inherit;
    text-wrap: pretty;
  }
  .card-header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-shrink: 0;
  }
</style>
