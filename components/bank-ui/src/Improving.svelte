<script>
  /* Improving — the curative-permit badge for a limit.

     A Limit may carry `allow: LIMIT_ALLOW_IMPROVING`. This is the
     curative-permit pattern: when a position is already in breach of the
     bound, an action that would otherwise be blocked is allowed THROUGH
     so long as it moves the position back toward compliance. (e.g. the
     available-balance floor — you can't push further negative, but you
     can pay money in.)

     Gold because it's a notable, benevolent exception — it reads as a
     small affordance, not an alarm. `title` carries the plain-English
     explanation on hover. */
</script>

<span
  class="qw-improving"
  title="Curative permit — a breaching action is allowed only when it moves the position back toward compliance."
>
  <svg viewBox="0 0 16 16" aria-hidden="true">
    <path d="M3 11 L7 6 L9.5 8.5 L13 4" />
    <path d="M13 7.5 V4 H9.5" />
  </svg>
  improving
</span>

<style>
  .qw-improving {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 20px;
    padding: 0 8px;
    border-radius: 999px;
    font-family: var(--grotesk);
    font-size: 11px;
    font-weight: 500;
    background: light-dark(oklch(0.93 0.07 82), oklch(0.30 0.07 78));
    color:      light-dark(oklch(0.46 0.12 70), oklch(0.86 0.12 84));
    cursor: help;
  }
  .qw-improving svg {
    width: 11px;
    height: 11px;
    stroke: currentColor;
    fill: none;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
</style>
