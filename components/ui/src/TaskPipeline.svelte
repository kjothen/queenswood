<script>
  /* TaskPipeline — a horizontal sequential stepper of a run's tasks.

     Each `step` is `{ name, status }` where status is one of
     ok | failed | running | skipped | pending (see pipelineSteps in
     jobs.js), plus `exception` — a deliberate negative beat (a reject,
     a divert, a decline) that is expected, not a failure, and so reads
     gold rather than red. Tasks run strictly in order; the connector
     between two nodes is solid once the preceding task is `ok` or
     `exception`, dashed otherwise. The running node's spinner respects
     reduced-motion.

     A step may carry `detail` — what the task actually did, e.g.
     "12,480 processed · 3.4s" — which replaces the status word beneath
     the name. A run that has recorded its tasks knows this; one being
     projected forward from a schedule does not, and falls back to the
     word. `alert` is a second line in the danger tone, for a figure
     that needs reading even though the task itself succeeded — a pass
     that finished with failed records is the case it exists for.

     `dense` shrinks the node for use inside a list rather than as the
     page's own pipeline.

     Pure visual — re-running is a whole-job action surfaced by the
     page (the API has no per-task re-run), so no buttons live here. */

  let { steps = [], dense = false } = $props();

  const label = {
    ok: "done",
    failed: "failed",
    running: "running…",
    skipped: "skipped",
    pending: "queued",
    exception: "flagged",
  };
</script>

<div class="pipeline" class:dense>
  {#each steps as step, i (step.name + i)}
    {#if i > 0}
      <div class="pipe-step">
        <span
          class="pipe-conn"
          class:solid={steps[i - 1].status === "ok" ||
            steps[i - 1].status === "exception"}
        ></span>
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
          {:else if step.status === "exception"}
            <svg viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="8.5" cy="8.5" r="6.6" />
              <path d="M8.5 5 V9" />
              <path d="M8.5 11.2 h0.01" />
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
          <span class="pt-dur">{step.detail ?? label[step.status] ?? step.status}</span>
          {#if step.alert}
            <span class="pt-alert">{step.alert}</span>
          {/if}
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
  .pipe-task .pt-alert {
    font-family: var(--mono);
    font-size: 10px;
    color: var(--danger);
  }

  /* Dense — the same nodes inside a run history row, where they sit
     under a line of run metadata rather than owning the section. */
  .pipeline.dense .pipe-task { gap: 7px; padding: 6px 10px; border-radius: 8px; }
  .pipeline.dense .pipe-task .pt-ico { width: 14px; height: 14px; }
  .pipeline.dense .pipe-task .pt-name { font-size: 11px; }
  .pipeline.dense .pipe-task .pt-dur,
  .pipeline.dense .pipe-task .pt-alert { font-size: 9.5px; }
  .pipeline.dense .pipe-conn { width: 18px; margin: 0 3px; }
  .pipe-task.ok { border-color: light-dark(oklch(0.80 0.05 145), oklch(0.40 0.05 145)); }
  .pipe-task.failed {
    border-color: var(--danger);
    background: light-dark(oklch(0.97 0.02 32), oklch(0.30 0.05 32));
  }
  .pipe-task.running {
    border-color: var(--gold);
    background: light-dark(oklch(0.97 0.025 84), oklch(0.31 0.055 80));
  }
  .pipe-task.exception {
    border-color: var(--gold);
    background: light-dark(oklch(0.975 0.02 84), oklch(0.30 0.045 80));
  }
  .pipe-task.skipped,
  .pipe-task.pending { opacity: 0.6; border-style: dashed; }
  .pipe-task .pt-ico.ok { color: var(--ok); }
  .pipe-task .pt-ico.failed { color: var(--danger); }
  .pipe-task .pt-ico.running { color: var(--gold-deep); }
  .pipe-task .pt-ico.exception { color: var(--gold-deep); }
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
