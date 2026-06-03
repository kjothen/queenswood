<script>
  /* Expander — disclosure chevron for an expandable tree-table row.

     Visual only. Wire the toggle on the parent <Tr expandable>; the
     chevron's rotation is driven by that row's `aria-expanded` state
     (see Table.svelte's tree CSS), so this component just draws the
     glyph and never holds state itself.

       <Tr expandable expanded={open} onclick={() => open = !open}>
         <Td expander><Expander /></Td>
         ...
       </Tr> */
</script>

<span class="qw-expander-btn" aria-hidden="true">
  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8"
       stroke-linecap="round" stroke-linejoin="round">
    <path d="M6 4 L10 8 L6 12" />
  </svg>
</span>

<!-- Styling lives in Table.svelte's :global tree block so the chevron
     shares the table's visual contract (hover surface, rotation on the
     account row's aria-expanded). No scoped styles here on purpose. -->
