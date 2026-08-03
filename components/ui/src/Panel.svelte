<script>
  /* Panel — the raised, hairline-bordered surface every detail screen
     stacks. Accounts, Ledger and Migrations all repeated the same three
     declarations before this existed.

     `pad` sets the body padding: "none" (default — the consumer owns
     its own padding, or the panel wraps a full-bleed table), "sm"
     (14px 20px), "md" (20px 24px) or "lg" (22px 24px).

         <Panel pad="lg">…</Panel>

         <Panel>
           <PanelHead title="Runs" count={2} note="one run per business day" />
           <Table>…</Table>
         </Panel> */

  let { pad = "none", children, ...rest } = $props();
</script>

<section class="qw-panel qw-panel--{pad}" {...rest}>
  {@render children?.()}
</section>

<style>
  .qw-panel {
    background: var(--surface-raised);
    border: 1px solid var(--rule-2);
    border-radius: 8px;
    min-width: 0;
  }
  .qw-panel--sm {
    padding: 14px 20px;
  }
  .qw-panel--md {
    padding: 20px 24px;
  }
  .qw-panel--lg {
    padding: 22px 24px;
  }
</style>
