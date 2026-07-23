<script>
  /* Effect — allow / deny pill for a capability.

     A Capability carries an Effect (proto enum EFFECT_ALLOW / EFFECT_DENY).
     This renders it as a small monospace pill, the same shape as <Phase>
     (a dimension chip), so allow/deny reads at a glance down a column:

       allow   pine    — the action is granted
       deny    red     — the action is explicitly refused (overrides an
                         inherited allow; emergency policies lean on this)

     Distinct from <Badge> (rounded record-status pill) on purpose. */

  let { effect = "allow" } = $props();
</script>

<span class="qw-effect qw-effect--{effect}">{effect}</span>

<style>
  .qw-effect {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 21px;
    padding: 0 8px;
    border-radius: 6px;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    font-weight: 500;
    white-space: nowrap;
  }
  .qw-effect::before {
    content: "";
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
  }
  .qw-effect--allow {
    background: light-dark(oklch(0.94 0.04 145),  oklch(0.26 0.05 145));
    color:      light-dark(oklch(0.34 0.075 145), oklch(0.82 0.06 145));
  }
  .qw-effect--deny {
    background: light-dark(oklch(0.94 0.04 30),  oklch(0.27 0.055 30));
    color:      light-dark(oklch(0.45 0.12 30),  oklch(0.84 0.10 30));
  }
</style>
