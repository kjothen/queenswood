<script>
  /* BankStateBand — a horizontal evidence strip: a row of figure cells
     followed by a flexible "attention" cell. The figures come from
     `cells` (each `{ figure, unit?, label, muted? }`); a muted cell
     reads as "still zero". The attention cell is composed from snippets
     (`icon`, `title`, `sub`, `action`) so the page owns the copy, and
     its tone (`idle | good | gold`) tints the icon tile.

     Lifted from the Jobs summary band; the attention cell is the new
     part — it narrates the cumulative state in one line. */

  let {
    cells = [],
    attentionTone = "idle",
    icon,
    title,
    sub,
    action,
  } = $props();
</script>

<section class="summary">
  {#each cells as c (c.label)}
    <div class="summary-cell" class:muted={c.muted}>
      <span class="figure">
        {c.figure}{#if c.unit}<span class="unit">{c.unit}</span>{/if}
      </span>
      <span class="label">{c.label}</span>
    </div>
  {/each}
  <div class="summary-attn {attentionTone}">
    {#if icon}<span class="a-ico">{@render icon()}</span>{/if}
    <div class="a-text">
      {#if title}<div class="a-title">{@render title()}</div>{/if}
      {#if sub}<div class="a-sub">{@render sub()}</div>{/if}
    </div>
    {#if action}<div class="a-action">{@render action()}</div>{/if}
  </div>
</section>

<style>
  .summary {
    display: flex;
    align-items: stretch;
    border: 1px solid var(--rule-2);
    border-radius: 8px;
    background: var(--surface-raised);
    overflow: hidden;
  }
  .summary-cell {
    padding: 13px 22px;
    display: flex;
    flex-direction: column;
    gap: 3px;
    border-right: 1px solid var(--rule-2);
    justify-content: center;
  }
  .summary-cell .figure {
    font-size: 23px;
    font-weight: 500;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    color: var(--fg);
    display: flex;
    align-items: baseline;
    gap: 7px;
  }
  .summary-cell .figure .unit {
    font-size: 13px;
    color: var(--fg-muted);
    font-weight: 400;
    white-space: nowrap;
  }
  .summary-cell .label {
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .summary-cell.muted .figure { color: var(--fg-muted); }

  .summary-attn {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 13px 22px;
    min-width: 0;
  }
  .summary-attn .a-ico {
    width: 28px;
    height: 28px;
    border-radius: 7px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
  }
  .summary-attn .a-ico :global(svg) {
    width: 15px;
    height: 15px;
    stroke: currentColor;
    fill: none;
    stroke-width: 1.7;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  .summary-attn.good .a-ico {
    background: light-dark(oklch(0.92 0.045 145), oklch(0.27 0.05 145));
    color: var(--ok);
  }
  .summary-attn.idle .a-ico {
    background: var(--surface-sunk);
    color: var(--fg-muted);
  }
  .summary-attn.gold .a-ico {
    background: light-dark(oklch(0.93 0.05 80), oklch(0.30 0.06 78));
    color: var(--gold-deep);
  }
  .summary-attn .a-text { min-width: 0; }
  .summary-attn .a-title { font-size: 13px; font-weight: 500; color: var(--fg); }
  .summary-attn .a-sub { font-size: 12px; color: var(--fg-muted); margin-top: 1px; }
  .summary-attn .a-sub :global(.mono) { font-family: var(--mono); }
  .summary-attn .a-action { margin-left: auto; }

  @media (max-width: 1000px) {
    .summary { flex-wrap: wrap; }
    .summary-attn { flex-basis: 100%; border-top: 1px solid var(--rule-2); }
  }
</style>
