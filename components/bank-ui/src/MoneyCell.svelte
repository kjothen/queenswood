<script>
  /* MoneyCell — a <td> for a currency figure.

     Holds the design system's money rules in one place:
       • right-aligned, monospace, tabular figures (columns line up)
       • sign tone — negative → danger, zero → muted, positive → default
       • optional `meta` subline (e.g. "3 balances")
       • `emphasized` for the account total vs. its lighter child balances

     Pass minor units + currency; formatting happens here so every figure
     in both consoles renders identically.

       <MoneyCell minor={acc.available} ccy="GBP" emphasized meta="3 balances" />
       <MoneyCell minor={b.minor} ccy="GBP" /> */

  import { formatMoney, moneyTone } from "./money.js";

  let {
    minor,
    ccy = "GBP",
    meta,
    emphasized = false,
    ...rest
  } = $props();

  const tone = $derived(moneyTone(minor));
</script>

<td
  class="qw-money qw-money--{tone}"
  class:qw-money-emph={emphasized}
  {...rest}
>
  {formatMoney(minor, ccy)}
  {#if meta}<span class="qw-money-meta">{meta}</span>{/if}
</td>

<style>
  .qw-money {
    text-align: right;
    font-family: var(--mono);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    color: var(--fg-2);
  }
  /* Emphasis = the account total. Heavier + slightly larger. */
  .qw-money-emph { font-size: 13.5px; font-weight: 500; color: var(--fg); }

  /* Sign tones declared AFTER emphasis so they win the color (same
     specificity); compounds cover the emphasized + signed case. */
  .qw-money--neg,
  .qw-money-emph.qw-money--neg  { color: var(--danger); }
  .qw-money--zero,
  .qw-money-emph.qw-money--zero { color: var(--fg-muted); }

  .qw-money-meta {
    display: block;
    font-family: var(--mono);
    font-size: 10px;
    color: var(--fg-muted);
    font-weight: 400;
    margin-top: 2px;
    letter-spacing: 0.02em;
  }
</style>
