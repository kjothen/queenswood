<script>
  /* SidenavGroup — a labelled section inside a Sidenav.
     The title is hidden when Sidenav is collapsed. Visual contract
     defined in Sidenav.svelte; this just emits the right DOM. */

  let { title = "", children } = $props();
</script>

<div class="sidenav-group">
  {#if title}<span class="sidenav-grouptitle">{title}</span>{/if}
  {@render children?.()}
</div>
