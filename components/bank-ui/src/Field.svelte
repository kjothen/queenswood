<script>
  /* Field — the label/input/hint trio.

       <Field label="Product name" htmlFor="f-name"
              hint="Defaults to {currency} {type}.">
         <Input id="f-name" bind:value={name} />
       </Field>

     `hint` accepts a string OR a snippet for richer markup:

         <Field label="Interest rate" htmlFor="f-rate">
           <Input id="f-rate" type="number" bind:value={rate} affix="bps" />
           {#snippet hint()}
             Basis points. <code>100 bps = 1.00%</code>.
           {/snippet}
         </Field>
  */

  let { label, hint, htmlFor, children } = $props();
</script>

<div class="field">
  {#if label}
    <label class="field-label" for={htmlFor}>{label}</label>
  {/if}
  {@render children?.()}
  {#if typeof hint === "string"}
    <span class="field-hint">{hint}</span>
  {:else if hint}
    <span class="field-hint">{@render hint()}</span>
  {/if}
</div>

<style>
  .field { display: flex; flex-direction: column; gap: 6px; }
  .field-label {
    font-family: var(--grotesk);
    font-size: 13px;
    font-weight: 500;
    color: var(--fg);
    line-height: 1.2;
  }
  .field-hint {
    font-size: 12px;
    color: var(--fg-muted);
    margin-top: 4px;
    line-height: 1.4;
  }
  .field-hint :global(code) {
    font-family: var(--mono);
    font-size: 11px;
    background: var(--surface-sunk);
    padding: 1px 5px;
    border-radius: 3px;
    color: var(--fg-2);
  }
</style>
