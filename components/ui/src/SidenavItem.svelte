<script>
  /* SidenavItem — a navigation entry inside a SidenavGroup.

     Pass `href` for a link (default), or omit `href` and pass `onclick`
     for a button. Pass `current` to mark this as the active page (left
     stripe + tinted background). Use the `icon` snippet for the leading
     glyph; children become the label.

       <SidenavItem href="/products" current title="Products">
         {#snippet icon()}<svg viewBox="0 0 16 16">...</svg>{/snippet}
         Products
       </SidenavItem>

     The `title` prop becomes the native tooltip — important for the
     icon-only collapsed state where the label is hidden. */

  let {
    href,
    current = false,
    title,
    icon,
    children,
    onclick,
    ...rest
  } = $props();
</script>

{#if href}
  <a
    class="sidenav-item"
    {href}
    aria-current={current ? "page" : undefined}
    {title}
    {onclick}
    {...rest}
  >
    {#if icon}{@render icon()}{/if}
    <span class="sidenav-label">{@render children?.()}</span>
  </a>
{:else}
  <button
    type="button"
    class="sidenav-item"
    aria-current={current ? "page" : undefined}
    {title}
    {onclick}
    {...rest}
  >
    {#if icon}{@render icon()}{/if}
    <span class="sidenav-label">{@render children?.()}</span>
  </button>
{/if}
