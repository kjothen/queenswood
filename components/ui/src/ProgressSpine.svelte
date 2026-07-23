<script>
  /* ProgressSpine — a horizontal narrative arc: numbered nodes joined
     by connectors, one per step. Sibling of TaskPipeline, but for a
     whole multi-step journey rather than a single run's tasks.

     Each `step` is `{ num, label, status }` where status is one of
     locked | ready | running | done. A done node fills; a ready node
     wears a gold ring; a running node spins; a locked node shows a
     lock. The connector to a node's right fills once that node is done.
     Clicking a node calls `onJump(index)`. The running ring and the
     spinner respect reduced-motion. */

  let {
    title = "",
    steps = [],
    progressLabel = "steps run",
    onJump,
  } = $props();

  const doneCount = $derived(
    steps.filter((s) => s.status === "done").length,
  );
</script>

<section class="arc">
  <div class="arc-top">
    <span class="arc-title">{title}</span>
    <span class="arc-prog">
      <span class="pn">{doneCount}</span> of {steps.length} {progressLabel}
    </span>
  </div>
  <div class="spine">
    {#each steps as s, i (s.num)}
      <div class="spine-step">
        <button
          type="button"
          class="spine-node {s.status}"
          title={s.label}
          onclick={() => onJump?.(i)}
        >
          <span class="spine-dot">
            {#if s.status === "done"}
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3.5 8.5 L6.5 11.5 L12.5 5" />
              </svg>
            {:else if s.status === "locked"}
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3.5" y="7" width="9" height="6.5" rx="1.2" />
                <path d="M5.5 7 V5 a2.5 2.5 0 0 1 5 0 V7" />
              </svg>
            {:else}
              {s.num}
            {/if}
          </span>
          <span class="spine-label">{s.label}</span>
        </button>
        {#if i < steps.length - 1}
          <div class="spine-conn" class:filled={s.status === "done"}></div>
        {/if}
      </div>
    {/each}
  </div>
</section>

<style>
  .arc {
    background: var(--surface-raised);
    border: 1px solid var(--rule-2);
    border-radius: 10px;
    padding: 20px 24px 22px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .arc-top {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px 24px;
    flex-wrap: wrap;
  }
  .arc-title {
    font-family: var(--serif);
    font-style: italic;
    font-weight: 500;
    font-size: 21px;
    line-height: 1.15;
    color: var(--fg);
    letter-spacing: 0.003em;
  }
  .arc-prog {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.04em;
    color: var(--fg-muted);
  }
  .arc-prog .pn { color: var(--gold-deep); }

  .spine { display: flex; align-items: flex-start; }
  .spine-step { display: flex; align-items: flex-start; flex: 1 1 0; min-width: 0; }
  .spine-node {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 7px;
    flex: 0 0 auto;
    cursor: pointer;
    background: none;
    border: none;
    padding: 0;
    width: 92px;
    color: inherit;
  }
  .spine-dot {
    position: relative;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-family: var(--mono);
    font-size: 12px;
    font-weight: 500;
    border: 1.5px solid var(--rule);
    background: var(--surface-sunk);
    color: var(--fg-muted);
    transition: all 0.18s;
    flex: 0 0 auto;
  }
  .spine-dot svg {
    width: 15px;
    height: 15px;
    stroke: currentColor;
    fill: none;
  }
  .spine-node.done .spine-dot {
    background: var(--primary);
    border-color: var(--primary);
    color: var(--primary-fg);
  }
  .spine-node.ready .spine-dot {
    border-color: var(--gold);
    color: var(--gold-deep);
    box-shadow: 0 0 0 3px light-dark(oklch(0.93 0.055 80), oklch(0.30 0.060 78));
  }
  .spine-node.running .spine-dot {
    border-color: var(--gold);
    color: var(--gold-deep);
  }
  .spine-node.running .spine-dot::after {
    content: "";
    position: absolute;
    inset: -1.5px;
    border-radius: 50%;
    border: 1.5px solid var(--gold);
    border-right-color: transparent;
    animation: qw-spine-spin 0.9s linear infinite;
  }
  .spine-label {
    font-size: 11px;
    color: var(--fg-muted);
    text-align: center;
    line-height: 1.25;
    max-width: 88px;
    transition: color 0.18s;
  }
  .spine-node.done .spine-label,
  .spine-node.ready .spine-label,
  .spine-node.running .spine-label { color: var(--fg-2); }
  .spine-node:hover .spine-label { color: var(--fg); }
  .spine-conn {
    flex: 1 1 auto;
    height: 2px;
    background: var(--rule);
    margin-top: 14px;
    border-radius: 2px;
    transition: background 0.3s;
    min-width: 12px;
  }
  .spine-conn.filled { background: var(--primary); }

  @keyframes qw-spine-spin { to { transform: rotate(360deg); } }
  @media (prefers-reduced-motion: reduce) {
    .spine-node.running .spine-dot::after { animation: none; }
  }
  @media (max-width: 1000px) {
    .spine-label { display: none; }
  }
</style>
