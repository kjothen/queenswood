<script>
  let { children, ...rest } = $props();
</script>

<thead {...rest}>{@render children?.()}</thead>
