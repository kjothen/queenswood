<script>
  /* Bound — the headline figure for a limit.

     Renders a Bound (max / min / range) as the bound's HERO: the operator
     word, the formatted figure, its unit, and a window pill. This is the
     thing the eye should land on in the Limits column — the reason sits
     below it as supporting text.

       <Bound bound={{ kind:"max", aggregate:{ type:"count", value:100000, window:"daily" } }} />
         →  MAX  100,000 count   / day

       <Bound bound={{ kind:"min", aggregate:{ type:"amount", minor:0, ccy:"GBP", window:"instant" } }} />
         →  MIN  £0 GBP   instant

       <Bound bound={{ kind:"range", min:{…£1}, max:{…£10,000} }} />
         →  RANGE  £1.00 – £10,000.00 GBP   instant

     `instant` windows tint violet to flag a point-in-time check vs. a
     per-period total. Drop <Bound> into a flex row (gap ~8px) alongside
     <Improving> when the limit is a curative permit. */

  import { formatAggregate, boundOp, boundWindow, WINDOW_LABEL } from "./bounds.js";

  let { bound } = $props();

  const op = $derived(boundOp(bound));
  const window = $derived(boundWindow(bound));
  const isInstant = $derived(window === "instant");

  // For max/min: a single figure. For range: low – high (shared unit).
  const parts = $derived.by(() => {
    if (bound.kind === "range") {
      const lo = formatAggregate(bound.min);
      const hi = formatAggregate(bound.max);
      return { value: `${lo.value} – ${hi.value}`, unit: hi.unit };
    }
    return formatAggregate(bound.aggregate);
  });
</script>

<span class="qw-bound">
  <span class="b-op">{op}</span>
  <span class="b-val">{parts.value}</span>
  <span class="b-unit">{parts.unit}</span>
</span>
<span class="qw-window" class:qw-window--instant={isInstant}>{WINDOW_LABEL[window] ?? window}</span>

<style>
  .qw-bound {
    display: inline-flex;
    align-items: baseline;
    gap: 7px;
    font-family: var(--mono);
    white-space: nowrap;
  }
  .qw-bound .b-op {
    font-size: 11px;
    color: var(--fg-muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  .qw-bound .b-val {
    font-size: 15px;
    font-weight: 500;
    color: var(--fg);
    letter-spacing: -0.01em;
    font-variant-numeric: tabular-nums;
  }
  .qw-bound .b-unit { font-size: 11px; color: var(--fg-muted); }

  .qw-window {
    display: inline-flex;
    align-items: center;
    height: 18px;
    padding: 0 7px;
    margin-left: 7px;
    border-radius: 4px;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.02em;
    white-space: nowrap;
    background: var(--surface-sunk);
    color: var(--fg-muted);
    border: 1px solid var(--rule-2);
  }
  .qw-window--instant { color: light-dark(oklch(0.4 0.08 270), oklch(0.78 0.07 270)); }
</style>
