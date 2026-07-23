<script>
  let { children, ...rest } = $props();
</script>

<tbody {...rest}>{@render children?.()}</tbody>
