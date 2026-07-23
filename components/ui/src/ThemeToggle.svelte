<script>
  /* Tri-state theme cycler.
     Click cycles: auto → light → dark → auto.
     Icon mirrors the user's *preference*, not the resolved theme — so
     someone on Auto sees the half-disc icon even when the system happens
     to be in dark mode. That communicates the actual setting honestly. */

  import { themeState, setTheme, resolvedTheme } from "./theme.svelte.js";

  const NEXT_PREF   = { auto: "light", light: "dark", dark: "auto" };
  const NEXT_LABEL  = { auto: "light", light: "dark", dark: "follow system" };

  function cycle() {
    setTheme(NEXT_PREF[themeState.pref]);
  }

  let pref     = $derived(themeState.pref);
  let resolved = $derived(resolvedTheme());
</script>

<button
  type="button"
  class="theme-toggle"
  aria-label="Theme: {pref}. Click to switch to {NEXT_LABEL[pref]}."
  title="Theme: {pref} ({resolved}) · click to cycle"
  onclick={cycle}
  data-pref={pref}
  data-resolved={resolved}
>
  {#if pref === "auto"}
    <!-- Half disc — symbolises "follow system, whichever side it is". -->
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <circle cx="8" cy="8" r="5.25" fill="none" stroke="currentColor" stroke-width="1.2" />
      <path d="M8 2.75 A 5.25 5.25 0 0 1 8 13.25 Z" fill="currentColor" />
    </svg>
  {:else if pref === "light"}
    <!-- Sun -->
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <circle cx="8" cy="8" r="3" fill="currentColor" />
      <g stroke="currentColor" stroke-width="1.3" stroke-linecap="round">
        <line x1="8" y1="1.5" x2="8" y2="3" />
        <line x1="8" y1="13" x2="8" y2="14.5" />
        <line x1="1.5" y1="8" x2="3" y2="8" />
        <line x1="13" y1="8" x2="14.5" y2="8" />
        <line x1="3.3" y1="3.3" x2="4.4" y2="4.4" />
        <line x1="11.6" y1="11.6" x2="12.7" y2="12.7" />
        <line x1="3.3" y1="12.7" x2="4.4" y2="11.6" />
        <line x1="11.6" y1="4.4" x2="12.7" y2="3.3" />
      </g>
    </svg>
  {:else}
    <!-- Moon -->
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <path
        d="M13 9.7 A 5.2 5.2 0 1 1 6.3 3 A 4.2 4.2 0 0 0 13 9.7 Z"
        fill="currentColor"
      />
    </svg>
  {/if}
</button>

<style>
  .theme-toggle {
    height: 32px;
    width: 32px;
    padding: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    border: 1px solid var(--rule);
    background: transparent;
    color: var(--fg);
    cursor: pointer;
    transition:
      background 0.12s,
      color 0.12s,
      transform 0.08s,
      border-color 0.12s;
  }
  .theme-toggle:hover {
    background: var(--hover-overlay);
    border-color: var(--rule);
  }
  .theme-toggle:active { transform: translateY(0.5px); }
  .theme-toggle:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: 2px;
  }
</style>
