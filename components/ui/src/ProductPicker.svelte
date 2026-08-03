<script>
  /* ProductPicker — a searchable product chooser.

     A native <select> can't carry what the choice actually turns on:
     the product's type, how many published versions it has, and how
     many live accounts sit under it. So the trigger shows the current
     choice with that meta line, and the panel is a searchable list.

         <ProductPicker
           products={sourceProducts}
           value={draft.sourceProductId}
           onselect={(p) => pickSource(p)}
           open={picking === "source"}
           ontoggle={() => picking = picking === "source" ? null : "source"}
           hiddenNote="5 products hidden — a migration's target must be
                       the same product type as its source (savings)." />

     Each product is `{ id, name, type, publishedCount, accountCount }`.
     `accountCount` may be undefined — the row then omits it rather than
     showing a fabricated figure.

     `hiddenNote` explains an absence. A list silently shortened by a
     type filter reads as a missing product, so say why it's short. */

  let {
    products = [],
    value = null,
    open = false,
    onselect,
    ontoggle,
    hiddenNote,
    label = "Product",
  } = $props();

  let query = $state("");
  let searchEl = $state(null);

  const selected = $derived(products.find((p) => p.id === value) ?? null);

  const filtered = $derived.by(() => {
    const q = query.trim().toLowerCase();
    if (!q) return products;
    return products.filter((p) =>
      `${p.name} ${p.type}`.toLowerCase().includes(q),
    );
  });

  // Opening clears the last search and drops the caret into it, so the
  // picker is type-to-find from the first keystroke.
  $effect(() => {
    if (open) {
      query = "";
      queueMicrotask(() => searchEl?.focus());
    }
  });

  function meta(p) {
    const parts = [p.type];
    if (typeof p.publishedCount === "number") {
      parts.push(`${p.publishedCount} published`);
    }
    if (typeof p.accountCount === "number") {
      parts.push(`${p.accountCount.toLocaleString("en-GB")} accounts`);
    }
    return parts.join(" · ");
  }
</script>

<div class="qw-picker" class:open>
  <button
    type="button"
    class="qw-picker-trigger"
    aria-haspopup="listbox"
    aria-expanded={open}
    aria-label={label}
    onclick={() => ontoggle?.()}
  >
    <span class="qw-picker-chosen">
      {#if selected}
        <span class="qw-picker-name">{selected.name}</span>
        <span class="qw-picker-meta">{meta(selected)}</span>
      {:else}
        <span class="qw-picker-name qw-picker-none">Choose a product</span>
      {/if}
    </span>
    <span class="qw-picker-right">
      {#if selected}<span class="qw-picker-tag">{selected.type}</span>{/if}
      <svg
        class="qw-picker-chevron"
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M4 6.5 L8 10.5 L12 6.5" />
      </svg>
    </span>
  </button>

  {#if open}
    <div class="qw-picker-panel">
      <div class="qw-picker-search">
        <svg
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="7" cy="7" r="4.5" />
          <path d="M10.5 10.5 L14 14" />
        </svg>
        <input
          bind:this={searchEl}
          bind:value={query}
          type="text"
          autocomplete="off"
          placeholder="Search products…"
          aria-label="Search products"
        />
      </div>
      <div class="qw-picker-list" role="listbox" aria-label={label}>
        {#if filtered.length === 0}
          <div class="qw-picker-empty">No product matches that search.</div>
        {:else}
          {#each filtered as p (p.id)}
            <button
              type="button"
              class="qw-picker-row"
              role="option"
              aria-selected={p.id === value}
              onclick={() => onselect?.(p)}
            >
              <svg
                class="qw-picker-check"
                viewBox="0 0 16 16"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M3.5 8.4 L6.5 11.3 L12.5 5" />
              </svg>
              <span class="qw-picker-rowtext">
                <span class="qw-picker-rowname">{p.name}</span>
                <span class="qw-picker-meta">{meta(p)}</span>
              </span>
              <span class="qw-picker-tag">{p.type}</span>
            </button>
          {/each}
        {/if}
      </div>
      {#if hiddenNote}
        <div class="qw-picker-foot">{hiddenNote}</div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .qw-picker {
    position: relative;
  }
  .qw-picker-trigger {
    width: 100%;
    min-height: 46px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 8px 12px;
    border: 1px solid var(--rule);
    border-radius: 6px;
    background: var(--surface);
    color: var(--fg);
    font: inherit;
    text-align: left;
    cursor: pointer;
    transition: border-color 0.12s, background 0.12s;
  }
  .qw-picker-trigger:hover {
    border-color: light-dark(rgba(20, 15, 10, 0.18), rgba(244, 241, 234, 0.2));
  }
  .qw-picker-trigger:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: 2px;
  }
  .qw-picker.open .qw-picker-trigger {
    border-color: var(--gold);
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
  }
  .qw-picker-chosen {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }
  .qw-picker-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--fg);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .qw-picker-none {
    color: var(--fg-muted);
    font-weight: 400;
  }
  .qw-picker-meta {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .qw-picker-right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
  }
  .qw-picker-tag {
    font-family: var(--mono);
    font-size: 10.5px;
    color: var(--fg-muted);
    background: var(--surface-sunk);
    border: 1px solid var(--rule-2);
    border-radius: 4px;
    padding: 2px 6px;
    white-space: nowrap;
  }
  .qw-picker-chevron {
    width: 14px;
    height: 14px;
    color: var(--fg-muted);
    transition: transform 0.14s ease;
  }
  .qw-picker.open .qw-picker-chevron {
    transform: rotate(180deg);
  }

  .qw-picker-panel {
    position: relative;
    z-index: 3;
    border: 1px solid var(--gold);
    border-top: none;
    border-radius: 0 0 6px 6px;
    background: var(--surface-raised);
    overflow: hidden;
  }
  .qw-picker-search {
    position: relative;
    border-bottom: 1px solid var(--rule-2);
  }
  .qw-picker-search svg {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 13px;
    height: 13px;
    color: var(--fg-muted);
    pointer-events: none;
  }
  .qw-picker-search input {
    width: 100%;
    height: 36px;
    padding: 0 12px 0 32px;
    border: none;
    background: transparent;
    color: var(--fg);
    font: inherit;
    font-size: 13px;
  }
  .qw-picker-search input:focus {
    outline: 2px solid var(--gold);
    outline-offset: -1px;
  }
  .qw-picker-list {
    max-height: 232px;
    overflow-y: auto;
  }
  .qw-picker-row {
    width: 100%;
    display: grid;
    grid-template-columns: 14px minmax(0, 1fr) auto;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border: none;
    background: transparent;
    color: var(--fg);
    font: inherit;
    text-align: left;
    cursor: pointer;
    transition: background 0.1s;
  }
  .qw-picker-row:hover {
    background: var(--hover-overlay);
  }
  .qw-picker-row:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: -2px;
  }
  .qw-picker-row[aria-selected="true"] {
    background: light-dark(oklch(0.95 0.02 145), oklch(0.24 0.035 145));
  }
  .qw-picker-check {
    width: 14px;
    height: 14px;
    color: var(--pine-4);
    opacity: 0;
  }
  .qw-picker-row[aria-selected="true"] .qw-picker-check {
    opacity: 1;
  }
  .qw-picker-rowtext {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .qw-picker-rowname {
    font-size: 13.5px;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .qw-picker-row .qw-picker-meta {
    font-size: 10.5px;
  }
  .qw-picker-empty {
    padding: 24px 12px;
    text-align: center;
    font-size: 13px;
    color: var(--fg-muted);
  }
  .qw-picker-foot {
    padding: 9px 12px;
    background: var(--surface-sunk);
    border-top: 1px solid var(--rule-2);
    font-size: 11.5px;
    line-height: 1.45;
    color: var(--fg-muted);
  }
</style>
