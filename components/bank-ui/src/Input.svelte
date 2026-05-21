<script>
  /* Input — single-line text input.

     Set `affix` for a right-aligned unit hint (e.g. "bps", "%", "GBP")
     that sits inside the input visually but isn't editable.

         <Input bind:value={rate} type="number" affix="bps" />

     Bindable so two-way bindings work: <Input bind:value={x} />. */

  let {
    type = "text",
    value = $bindable(""),
    affix,
    ...rest
  } = $props();
</script>

{#if affix}
  <div class="input-affix">
    <input class="input" {type} bind:value {...rest} />
    <span class="affix">{affix}</span>
  </div>
{:else}
  <input class="input" {type} bind:value {...rest} />
{/if}

<style>
  :global(.input) {
    height: 40px;
    width: 100%;
    padding: 0 12px;
    border-radius: 6px;
    border: 1px solid var(--rule);
    background: var(--surface);
    color: var(--fg);
    font: inherit;
    font-size: 14px;
    transition: border-color 0.12s, background 0.12s;
  }
  :global(.input:hover) {
    border-color: light-dark(rgba(20, 15, 10, 0.18), rgba(244, 241, 234, 0.2));
  }
  :global(.input:focus) {
    outline: 2px solid var(--gold);
    outline-offset: -1px;
    border-color: var(--gold);
  }
  :global(.input::placeholder) { color: var(--fg-muted); }

  .input-affix { position: relative; }
  .input-affix :global(.input) { padding-right: 44px; }
  .input-affix .affix {
    position: absolute;
    right: 12px;
    top: 50%;
    transform: translateY(-50%);
    font-family: var(--mono);
    font-size: 12px;
    color: var(--fg-muted);
    pointer-events: none;
  }
</style>
