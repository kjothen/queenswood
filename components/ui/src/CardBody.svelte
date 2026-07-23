<script>
  /* CardBody — main content area of a Card.
     Styles its <p> children to match the card's body color (which
     adapts per variant via the --card-body-color custom property
     set on the Card root). */

  let { children } = $props();
</script>

<div class="card-body">
  {@render children?.()}
</div>

<style>
  .card-body {
    color: inherit;
    line-height: 1.55;
  }
  .card-body :global(p) {
    margin: 0;
    font-size: 14px;
    line-height: 1.55;
    color: var(--card-body-color, var(--fg-2));
    text-wrap: pretty;
  }
  .card-body :global(p + p) { margin-top: 10px; }
  .card-body :global(code) {
    font-family: var(--mono);
    font-size: 12.5px;
    background: var(--surface-sunk);
    padding: 1px 5px;
    border-radius: 3px;
    color: var(--fg);
  }
</style>
