<script>
  /* Td — table body cell with visual modifiers.

       mono        monospace + slightly smaller font (ids, codes)
       muted       --fg-muted color (secondary info)
       emphasized  --fg + weight 500 (primary value in the row)
       tabular     tabular numeric figures (numbers in columns)
       align       "right" | "center" (default left)

     Combine freely: <Td mono muted>...</Td>, <Td align="right" mono tabular>...</Td>. */

  let {
    mono = false,
    muted = false,
    emphasized = false,
    tabular = false,
    align,
    children,
    ...rest
  } = $props();
</script>

<td
  class:qw-td-mono={mono}
  class:qw-td-muted={muted}
  class:qw-td-emphasized={emphasized}
  class:qw-td-tabular={tabular}
  class:qw-cell-right={align === "right"}
  class:qw-cell-center={align === "center"}
  {...rest}
>
  {@render children?.()}
</td>
