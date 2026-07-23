<script>
  /* CardFooter — bottom row, typically a small uppercase-mono link
     ("single-unified-api →"). Sticks to the bottom of the Card via
     `margin-top: auto` so equal-height card grids look tidy. */

  let { children } = $props();
</script>

<footer class="card-footer">
  {@render children?.()}
</footer>

<style>
  .card-footer {
    margin-top: auto;
    padding-top: 12px;
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--card-footer-color, var(--fg-muted));
  }
  .card-footer :global(a) {
    color: var(--card-footer-link, var(--fg));
    border-bottom: 1px solid var(--card-footer-rule, var(--rule));
    text-decoration: none;
    padding-bottom: 1px;
    transition: border-color 0.12s;
  }
  .card-footer :global(a:hover) {
    border-bottom-color: currentColor;
  }
</style>
