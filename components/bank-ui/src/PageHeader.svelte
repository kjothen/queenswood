<script>
  /* PageHeader — top-of-page identity. Editorial three-part stack:

       kicker   — small mono caps, gold-deep; typically org or context
       title    — Grotesk 22/500; the page name
       sub      — short explainer, one-liner is ideal

     The `actions` snippet renders to the right; arrange Buttons inside. */

  let {
    kicker,
    title,
    sub,
    actions,
  } = $props();
</script>

<header class="page-header">
  <div class="page-header-text">
    {#if kicker}<span class="page-kicker">{kicker}</span>{/if}
    {#if title}<h1 class="page-title">{title}</h1>{/if}
    {#if sub}<p class="page-sub">{sub}</p>{/if}
  </div>
  {#if actions}
    <div class="page-actions">
      {@render actions()}
    </div>
  {/if}
</header>

<style>
  .page-header {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 24px;
    padding-bottom: 4px;
  }
  .page-header-text {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .page-kicker {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--gold-deep);
  }
  .page-title {
    font-family: var(--grotesk);
    font-weight: 500;
    font-size: 22px;
    letter-spacing: -0.005em;
    line-height: 1.15;
    margin: 0;
    color: var(--fg);
  }
  .page-sub {
    font-size: 13px;
    color: var(--fg-muted);
    max-width: 60ch;
    margin: 6px 0 0 0;
  }
  .page-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
</style>
