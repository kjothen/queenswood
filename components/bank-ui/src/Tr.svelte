<script>
  let { children, ...rest } = $props();
</script>

<tr {...rest}>{@render children?.()}</tr>
