<script>
  /* TrialBalanceCard — one balanced block per currency. Shows the two
     legs (Σ debits / Σ credits in minor units) and, as the hero, the
     assertion: do they tie?

     The card derives `balanced` / `diff` itself from the two minor
     figures — integer equality on pence/cents, the only representation
     that can be trusted to tie. Don't pass a pre-computed flag; it could
     drift from the totals. `formatMoney` is display-only.

     A currency can legitimately sit out of balance intraday by the
     in-flight (pending-settlement) amount — not a data error, an
     unsettled leg. Out-of-balance reuses --danger (no bespoke alarm
     colour); only the assertion line + the 3px left edge change. */

  import { formatMoney } from "./money.js";

  let { ccy, sym, name, accounts, debitMinor, creditMinor } = $props();

  const diff = $derived(debitMinor - creditMinor);
  const balanced = $derived(diff === 0);
</script>

<div class="tb-card" class:ok={balanced} class:out={!balanced}>
  <div class="tb-card-head">
    <div class="tb-ccy">
      <span class="sym">{sym}</span>
      <span class="labels">
        <span class="code">{ccy}</span>
        <span class="nm">{name}</span>
      </span>
    </div>
    <span class="tb-count">{accounts} {accounts === 1 ? "account" : "accounts"}</span>
  </div>

  <div class="tb-legs">
    <div class="tb-leg">
      <span class="name"><span class="dot dot-debit"></span>Debits</span>
      <span class="amt">{formatMoney(debitMinor, ccy)}</span>
    </div>
    <div class="tb-leg">
      <span class="name"><span class="dot dot-credit"></span>Credits</span>
      <span class="amt">{formatMoney(creditMinor, ccy)}</span>
    </div>
  </div>

  <div class="tb-foot">
    {#if balanced}
      <div class="tb-check">
        <svg class="ico" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="8" cy="8" r="6.4" stroke-opacity="0.4" />
          <path d="M5.2 8.2 L7.1 10 L10.8 6" />
        </svg>
        <span class="txt">In balance</span>
      </div>
    {:else}
      <div class="tb-check">
        <svg class="ico" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <path d="M8 2.4 L14.4 13.2 H1.6 Z" />
          <path d="M8 6.6 V9.4" />
          <circle cx="8" cy="11.4" r="0.2" stroke-width="1.4" />
        </svg>
        <span class="txt">Out of balance</span>
      </div>
      <div class="tb-diff">{diff > 0 ? "Dr " : "Cr "}{formatMoney(Math.abs(diff), ccy)}</div>
      <div class="tb-hint">In-flight leg, pending settlement. Ties once it posts.</div>
    {/if}
  </div>
</div>

<style>
  .tb-card {
    position: relative;
    background: var(--surface-raised);
    border: 1px solid var(--rule-2);
    border-radius: 10px;
    padding: 16px 18px 14px;
    display: flex;
    flex-direction: column;
    gap: 14px;
    overflow: hidden;
  }
  .tb-card::before {
    content: "";
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: var(--ok);
    opacity: 0.5;
  }
  .tb-card.out::before {
    background: var(--danger);
    opacity: 1;
  }

  .tb-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .tb-ccy {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .tb-ccy .sym {
    width: 30px;
    height: 30px;
    border-radius: 7px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--surface-sunk);
    border: 1px solid var(--rule);
    font-family: var(--serif);
    font-size: 17px;
    color: var(--fg);
    line-height: 1;
  }
  .tb-ccy .labels {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  .tb-ccy .code {
    font-family: var(--mono);
    font-size: 13px;
    letter-spacing: 0.04em;
    color: var(--fg);
  }
  .tb-ccy .nm {
    font-size: 11px;
    color: var(--fg-muted);
  }
  .tb-count {
    font-family: var(--mono);
    font-size: 10px;
    color: var(--fg-muted);
    white-space: nowrap;
  }

  .tb-legs {
    display: flex;
    flex-direction: column;
    gap: 7px;
  }
  .tb-leg {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .tb-leg .name {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .tb-leg .dot {
    width: 8px;
    height: 8px;
    border-radius: 2px;
    flex: 0 0 auto;
  }
  .dot-debit {
    background: var(--debit);
  }
  .dot-credit {
    background: var(--credit);
  }
  .tb-leg .amt {
    font-family: var(--mono);
    font-size: 14px;
    font-variant-numeric: tabular-nums;
    color: var(--fg);
    white-space: nowrap;
  }

  .tb-foot {
    margin-top: 2px;
    padding-top: 12px;
    border-top: 1px solid var(--rule-2);
  }
  .tb-check {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .tb-check .ico {
    width: 16px;
    height: 16px;
    flex: 0 0 auto;
  }
  .tb-check .txt {
    font-size: 12.5px;
    font-weight: 500;
    white-space: nowrap;
  }
  .tb-card.ok .tb-check {
    color: var(--ok);
  }
  .tb-card.out .tb-check {
    color: var(--danger);
  }
  .tb-diff {
    margin-top: 7px;
    font-family: var(--mono);
    font-size: 14px;
    font-variant-numeric: tabular-nums;
    color: var(--danger);
    white-space: nowrap;
  }
  .tb-hint {
    margin-top: 6px;
    font-size: 11px;
    color: var(--fg-muted);
    line-height: 1.4;
  }
</style>
