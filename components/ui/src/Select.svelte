<script>
  /* Select — styled native <select>. Children should be <option>s.

         <Select bind:value={ccy}>
           <option>GBP</option>
           <option>EUR</option>
         </Select>

     The chevron is a data-URL SVG so the control needs no extra image
     asset. The select inherits the field's color-scheme so the native
     dropdown panel matches the theme on modern browsers. */

  let { value = $bindable(""), children, ...rest } = $props();
</script>

<select class="select" bind:value {...rest}>
  {@render children?.()}
</select>

<style>
  :global(.select) {
    height: 40px;
    width: 100%;
    padding: 0 36px 0 12px;
    border-radius: 6px;
    border: 1px solid var(--rule);
    background: var(--surface);
    color: var(--fg);
    font: inherit;
    font-size: 14px;
    transition: border-color 0.12s, background 0.12s;
    appearance: none;
    -webkit-appearance: none;
    background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='none' stroke='%236b645b' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'><path d='M4 6 L8 10 L12 6'/></svg>");
    background-repeat: no-repeat;
    background-position: right 12px center;
    background-size: 14px;
    cursor: pointer;
  }
  :global(.select:hover) {
    border-color: light-dark(rgba(20, 15, 10, 0.18), rgba(244, 241, 234, 0.2));
  }
  :global(.select:focus) {
    outline: 2px solid var(--gold);
    outline-offset: -1px;
    border-color: var(--gold);
  }
</style>
