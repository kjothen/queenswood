<script>
  /* Button — the system's general-purpose action.

     Variants encode INTENT, not color. Components ask for "primary"
     or "danger"; tokens.css decides the actual fill. This means we
     can retune the palette without touching consumers.

       primary    pine fill — the dominant action (1 per surface)
       brand      gold fill — elevation / "publish" / brand-coded action
       line       hairline border — secondary actions
       ghost      transparent — tertiary / inline actions
       danger     rust outline — destructive action; pair with `solid`
                  for irreversible destructive (e.g. "Delete forever")

     Sizes: sm (26px) / md (36px, default) / lg (44px).
     Modifiers: block (full-width). */

  let {
    variant = "line",
    size = "md",
    block = false,
    solid = false,
    type = "button",
    disabled = false,
    onclick,
    children,
    ...rest
  } = $props();
</script>

<button
  {type}
  class="btn {variant} {size}"
  class:block
  class:solid
  {disabled}
  {onclick}
  {...rest}
>
  {@render children?.()}
</button>

<style>
  .btn {
    height: 36px;
    padding: 0 16px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border-radius: 6px;
    font-family: var(--grotesk);
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 0.005em;
    border: 1px solid transparent;
    background: transparent;
    color: var(--fg);
    cursor: pointer;
    white-space: nowrap;
    transition:
      background 0.12s,
      border-color 0.12s,
      color 0.12s,
      transform 0.08s;
  }
  .btn:active { transform: translateY(0.5px); }
  .btn:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: 2px;
  }
  .btn:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
  .btn:disabled:active { transform: none; }
  .btn :global(svg) { width: 14px; height: 14px; }

  /* Sizes */
  .btn.sm { height: 26px; padding: 0 10px; font-size: 12px; gap: 5px; }
  .btn.sm :global(svg) { width: 12px; height: 12px; }
  .btn.lg { height: 44px; padding: 0 20px; font-size: 14px; }

  /* Variants */
  .btn.primary {
    background: var(--primary);
    color: var(--primary-fg);
  }
  .btn.primary:hover:not(:disabled) { background: var(--primary-hover); }

  .btn.brand {
    background: var(--brand-action);
    color: var(--brand-action-fg);
  }
  .btn.brand:hover:not(:disabled) { background: var(--brand-action-hover); }

  .btn.line {
    border-color: var(--rule);
    color: var(--fg);
    background: transparent;
  }
  .btn.line:hover:not(:disabled) {
    background: var(--hover-overlay);
    border-color: light-dark(rgba(20, 15, 10, 0.18), rgba(244, 241, 234, 0.2));
  }

  .btn.ghost {
    background: transparent;
    color: var(--fg-2);
  }
  .btn.ghost:hover:not(:disabled) {
    background: var(--hover-overlay);
    color: var(--fg);
  }

  .btn.danger {
    background: transparent;
    color: var(--danger);
    border-color: light-dark(rgba(160, 50, 30, 0.28), rgba(220, 110, 80, 0.32));
  }
  .btn.danger:hover:not(:disabled) {
    background: light-dark(rgba(160, 50, 30, 0.08), rgba(220, 110, 80, 0.12));
    border-color: var(--danger);
  }
  .btn.danger.solid {
    background: var(--danger);
    color: var(--danger-fg);
    border-color: transparent;
  }
  .btn.danger.solid:hover:not(:disabled) { background: var(--danger-hover); }

  /* Modifiers */
  .btn.block {
    width: 100%;
    justify-content: center;
  }
</style>
