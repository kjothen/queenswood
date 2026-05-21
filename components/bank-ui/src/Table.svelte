<script>
  /* Table — wrapped table with surface, hairlines, and consistent
     column chrome. Compose with Thead, Tbody, Tr, Th, Td.

         <Table>
           <Thead>
             <Tr>
               <Th>ID</Th>
               <Th>Name</Th>
               <Th align="right">Rate</Th>
             </Tr>
           </Thead>
           <Tbody>
             <Tr>
               <Td mono muted>prd.01...</Td>
               <Td emphasized>Current Account</Td>
               <Td align="right" mono tabular>265</Td>
             </Tr>
           </Tbody>
         </Table>

     Td variants (booleans): mono, muted, emphasized, tabular.
     Alignment via `align="right"` (or "center"). */

  let { children, ...rest } = $props();
</script>

<div class="table-wrap">
  <table class="qw-table" {...rest}>
    {@render children?.()}
  </table>
</div>

<style>
  .table-wrap {
    background: var(--surface-raised);
    border: 1px solid var(--rule-2);
    border-radius: 6px;
    overflow: hidden;
  }
  .qw-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
    color: var(--fg);
  }

  /* These selectors reach into Thead/Tbody/Tr/Th/Td descendants.
     :global is intentional — the Table component owns the visual
     contract; its children just emit semantic HTML. */
  :global(.qw-table thead th) {
    text-align: left;
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--fg-muted);
    font-weight: 500;
    padding: 12px 16px;
    background: var(--surface-sunk);
    border-bottom: 1px solid var(--rule-2);
    white-space: nowrap;
  }
  :global(.qw-table tbody td) {
    padding: 14px 16px;
    border-bottom: 1px solid var(--rule-2);
    vertical-align: middle;
    color: var(--fg-2);
  }
  :global(.qw-table tbody tr:last-child td) { border-bottom: none; }
  :global(.qw-table tbody tr:hover td) { background: var(--hover-overlay); }

  /* Td/Th utility classes — toggled by Td.svelte / Th.svelte props. */
  :global(.qw-td-mono)       { font-family: var(--mono); font-size: 12px; }
  :global(.qw-td-muted)      { color: var(--fg-muted); }
  :global(.qw-td-emphasized) { color: var(--fg); font-weight: 500; }
  :global(.qw-td-tabular)    { font-variant-numeric: tabular-nums; }
  :global(.qw-cell-right)    { text-align: right; }
  :global(.qw-cell-center)   { text-align: center; }
</style>
