<script>
  /* Th — table header cell. Use `align` for non-left alignment. */
  let { align, children, ...rest } = $props();
</script>

<th
  class:qw-cell-right={align === "right"}
  class:qw-cell-center={align === "center"}
  {...rest}
>
  {@render children?.()}
</th>
