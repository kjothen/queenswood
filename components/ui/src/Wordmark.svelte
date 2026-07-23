<script>
  /* Wordmark — two registers per the brand handoff.
     grotesk: QUEENS (Geist 300) + WOOD (Geist 500), 0.18em tracking.
     serif:   Queenswood, Cormorant Garamond 500, 0.04em tracking. */

  let { variant = "grotesk", size = 28, color } = $props();
</script>

{#if variant === "serif"}
  <span class="serif" style:font-size="{size}px" style:color={color}>Queenswood</span>
{:else}
  <span class="grotesk" style:font-size="{size}px" style:color={color}>
    <span class="light">QUEENS</span>WOOD
  </span>
{/if}

<style>
  .grotesk {
    font-family: var(--grotesk);
    font-weight: 500;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    line-height: 1;
    display: inline-block;
  }
  .grotesk .light { font-weight: 300; }

  .serif {
    font-family: var(--serif);
    font-weight: 500;
    letter-spacing: 0.04em;
    line-height: 1;
    display: inline-block;
  }
</style>
