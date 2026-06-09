<script>
  /* TaskPipeline — a horizontal sequential stepper of a run's tasks.

     Each `step` is `{ name, status }` where status is one of
     ok | failed | running | skipped | pending (see pipelineSteps in
     jobs.js). Tasks run strictly in order; the connector between two
     nodes is solid once the preceding task is `ok`, dashed otherwise.
     The running node's spinner respects reduced-motion.

     Pure visual — re-running is a whole-job action surfaced by the
     page (the API has no per-task re-run), so no buttons live here. */

  let { steps = [] } = $props();

  const label = {
    ok: "done",
    failed: "failed",
    running: "running…",
    skipped: "skipped",
    pending: "queued",
  };
</script>

<div class="pipeline">
  {#each steps as step, i (step.name + i)}
    {#if i > 0}
      <div class="pipe-step">
        <span class="pipe-conn" class:solid={steps[i - 1].status === "ok"}></span>
      </div>
    {/if}
    <div class="pipe-step">
      <div class="pipe-task {step.status}">
        <span class="pt-ico {step.status}">
          {#if step.status === "ok"}
            <svg viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="8.5" cy="8.5" r="6.6" />
              <path d="M5.6 8.7 L7.5 10.5 L11.4 6.4" />
            </svg>
          {:else if step.status === "failed"}
            <svg viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="8.5" cy="8.5" r="6.6" />
              <path d="M6.3 6.3 L10.7 10.7 M10.7 6.3 L6.3 10.7" />
            </svg>
          {:else if step.status === "running"}
            <svg viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <circle cx="8.5" cy="8.5" r="6.6" stroke-opacity="0.25" />
              <circle class="spin" cx="8.5" cy="8.5" r="6.6" stroke-dasharray="14 60" />
            </svg>
          {:else}
            <svg viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="8.5" cy="8.5" r="6.6" stroke-dasharray="2 3" />
              {#if step.status === "skipped"}<path d="M5.8 8.5 H11.2" />{/if}
            </svg>
          {/if}
        </span>
        <span class="pt-text">
          <span class="pt-name">{step.name}</span>
          <span class="pt-dur">{label[step.status] ?? step.status}</span>
        </span>
      </div>
    </div>
  {/each}
</div>

<style>
  .pipeline { display: flex; align-items: stretch; flex-wrap: wrap; gap: 0; }
  .pipe-step { display: flex; align-items: center; }
  .pipe-task {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 9px 13px;
    border: 1px solid var(--rule);
    border-radius: 9px;
    background: var(--surface-raised);
    min-width: 0;
  }
  .pipe-task .pt-ico { width: 17px; height: 17px; flex: 0 0 auto; }
  .pipe-task .pt-text { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  .pipe-task .pt-name {
    font-family: var(--mono);
    font-size: 12px;
    color: var(--fg);
    letter-spacing: 0.01em;
  }
  .pipe-task .pt-dur { font-family: var(--mono); font-size: 10px; color: var(--fg-muted); }
  .pipe-task.ok { border-color: light-dark(oklch(0.80 0.05 145), oklch(0.40 0.05 145)); }
  .pipe-task.failed {
    border-color: var(--danger);
    background: light-dark(oklch(0.97 0.02 32), oklch(0.30 0.05 32));
  }
  .pipe-task.running {
    border-color: var(--gold);
    background: light-dark(oklch(0.97 0.025 84), oklch(0.31 0.055 80));
  }
  .pipe-task.skipped,
  .pipe-task.pending { opacity: 0.6; border-style: dashed; }
  .pipe-task .pt-ico.ok { color: var(--ok); }
  .pipe-task .pt-ico.failed { color: var(--danger); }
  .pipe-task .pt-ico.running { color: var(--gold-deep); }
  .pipe-task .pt-ico.skipped,
  .pipe-task .pt-ico.pending { color: var(--fg-muted); }
  .pipe-task .pt-ico.running circle.spin {
    transform-origin: center;
    animation: qw-pipe-spin 1s linear infinite;
  }
  @keyframes qw-pipe-spin { to { transform: rotate(360deg); } }
  @media (prefers-reduced-motion: reduce) {
    .pipe-task .pt-ico.running circle.spin { animation: none; }
  }
  .pipe-conn {
    width: 30px;
    height: 0;
    border-top: 1.5px dashed var(--rule);
    margin: 0 4px;
    position: relative;
    flex: 0 0 auto;
  }
  .pipe-conn.solid { border-top-style: solid; }
  .pipe-conn::after {
    content: "";
    position: absolute;
    right: -1px;
    top: -3px;
    width: 6px;
    height: 6px;
    border-right: 1.5px solid var(--rule);
    border-bottom: 1.5px solid var(--rule);
    transform: rotate(-45deg);
  }
</style>
