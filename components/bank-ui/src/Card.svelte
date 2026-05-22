<script>
  /* Card — composable container for grouped content.

     Variants:
       default  surface-raised + hairline (the standard look)
       feature  inverted surface tone — ink in light, bone in dark.
                Use for one or two emphasis cards per grid.
       sunk     surface-sunk; recessed feel for inset content
       outline  transparent fill + hairline only

     Padding:
       compact (16) | default (24) | spacious (36) | none

     Interaction:
       Pass `href` to render the whole card as a link.
       Pass `onclick` to render as a button.
       Pass `clickable` for hover-lift without an action.

     Composition:
       <Card href="/adrs/0013">
         <CardHeader kicker="ADR-0013" title="One unified API." />
         <CardBody><p>Body copy here.</p></CardBody>
         <CardFooter><a href="/adrs/0013">single-unified-api →</a></CardFooter>
       </Card>

     The card is always a flex column with `gap: 12px`, so CardFooter's
     `margin-top: auto` sticks to the bottom in equal-height grids. */

  let {
    variant = "default",
    padding = "default",
    href,
    onclick,
    clickable = false,
    as,
    children,
    ...rest
  } = $props();

  let isClickable = $derived(!!(href || onclick || clickable));
  let element = $derived(as ?? (href ? "a" : onclick ? "button" : "article"));
</script>

<svelte:element
  this={element}
  class="card {variant} pad-{padding}"
  class:clickable={isClickable}
  {href}
  type={element === "button" ? "button" : undefined}
  {onclick}
  {...rest}
>
  {@render children?.()}
</svelte:element>

<style>
  .card {
    /* CSS vars children can read for tone-aware coloring */
    --card-kicker-color: var(--gold-deep);
    --card-body-color: var(--fg-2);
    --card-footer-color: var(--fg-muted);
    --card-footer-link: var(--fg);
    --card-footer-rule: var(--rule);

    display: flex;
    flex-direction: column;
    gap: 12px;
    background: var(--surface-raised);
    color: var(--fg);
    border: 1px solid var(--rule-2);
    border-radius: 12px;
    text-decoration: none;
    text-align: left;
    font-family: var(--grotesk);
    transition:
      background 0.12s,
      border-color 0.12s,
      transform 0.16s,
      box-shadow 0.16s;
  }
  button.card {
    appearance: none;
    font: inherit;
    color: inherit;
    cursor: pointer;
  }

  /* Variants */
  .card.feature {
    background: light-dark(var(--ink), oklch(0.86 0.02 70));
    color: light-dark(var(--paper), var(--ink));
    border-color: transparent;
    --card-kicker-color: light-dark(var(--gold-bright), var(--gold-deep));
    --card-body-color: light-dark(rgba(244, 241, 234, 0.72), rgba(20, 15, 10, 0.62));
    --card-footer-color: light-dark(rgba(244, 241, 234, 0.5), rgba(20, 15, 10, 0.5));
    --card-footer-link: light-dark(var(--paper), var(--ink));
    --card-footer-rule: light-dark(rgba(244, 241, 234, 0.2), rgba(20, 15, 10, 0.18));
  }
  .card.sunk {
    background: var(--surface-sunk);
    border-color: transparent;
  }
  .card.outline {
    background: transparent;
    border-color: var(--rule);
  }

  /* Padding */
  .card.pad-compact  { padding: 16px; }
  .card.pad-default  { padding: 24px; }
  .card.pad-spacious { padding: 36px; }
  .card.pad-none     { padding: 0; }

  /* Clickable */
  .card.clickable { cursor: pointer; }
  .card.clickable:hover {
    transform: translateY(-1px);
    box-shadow: light-dark(
      0 8px 24px -10px rgba(20, 15, 10, 0.14),
      0 8px 24px -10px rgba(0, 0, 0, 0.5)
    );
  }
  .card.clickable:active { transform: translateY(0); }
  .card.clickable:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: 2px;
  }
</style>
