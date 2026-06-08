<script>
  /* TrialBalance — the per-currency trial-balance band: a section header
     + legend (mapping debit/credit back to the GL type families) over a
     responsive grid of one balanced block per currency. Answers a single
     question, at a glance, before anyone scrolls the list: do the books
     tie today?

     Currencies never sum together — there is no grand total, only N
     balanced blocks (summing across currencies is meaningless). The band
     holds no list state; any card → filter-the-list interaction belongs
     to the page, not here. */

  import TrialBalanceCard from "./TrialBalanceCard.svelte";

  let { blocks = [], asOf = null } = $props();
</script>

<section class="tb-section">
  <div class="tb-section-head">
    <div class="tb-section-title">
      Trial balance
      {#if asOf}<span class="asof">· as of {asOf}</span>{/if}
    </div>
    <div class="tb-legend">
      <span><span class="dot dot-debit"></span>Debit (asset · expense)</span>
      <span><span class="dot dot-credit"></span>Credit (liability · equity · income)</span>
    </div>
  </div>
  <div class="tb-band">
    {#each blocks as b (b.ccy)}
      <TrialBalanceCard {...b} />
    {/each}
  </div>
</section>

<style>
  .tb-section {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .tb-section-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }
  .tb-section-title {
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--fg-muted);
    display: flex;
    align-items: center;
    gap: 9px;
  }
  .tb-section-title .asof {
    color: var(--fg-muted);
    opacity: 0.8;
  }
  .tb-legend {
    display: flex;
    gap: 16px;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.04em;
    color: var(--fg-muted);
    flex-wrap: wrap;
  }
  .tb-legend span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .tb-legend .dot {
    width: 8px;
    height: 8px;
    border-radius: 2px;
  }
  .dot-debit {
    background: var(--debit);
  }
  .dot-credit {
    background: var(--credit);
  }

  .tb-band {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
  @media (max-width: 880px) {
    .tb-band {
      grid-template-columns: 1fr;
    }
  }
</style>
