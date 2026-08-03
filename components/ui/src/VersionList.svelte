<script>
  /* VersionList — pick one or several versions of a product.

     `mode="multi"` renders checkboxes and `selected` is an array of
     version ids; `mode="single"` renders radios and `selected` is one
     id. `onchange` receives the next selection in the same shape.

         <VersionList
           mode="multi"
           title="Instant Access Savings · 5 versions"
           action={{ label: "Select all published", onclick: selectAll }}
           versions={sourceVersions}
           selected={draft.sourceVersionIds}
           onchange={(ids) => (draft.sourceVersionIds = ids)} />

     Each version is `{ id, number, meta, from, right, published }`.
     A non-published row dims its version number but stays selectable —
     on the target side that is deliberate, so choosing a draft raises
     the real `target-not-published` rejection rather than being
     silently impossible. */

  let {
    mode = "multi",
    versions = [],
    selected = mode === "multi" ? [] : null,
    onchange,
    title,
    action,
    name = "version",
  } = $props();

  const chosen = $derived(
    mode === "multi" ? new Set(selected ?? []) : new Set(selected ? [selected] : []),
  );

  function pick(id) {
    if (mode === "single") {
      onchange?.(id);
      return;
    }
    const next = new Set(chosen);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onchange?.(versions.filter((v) => next.has(v.id)).map((v) => v.id));
  }
</script>

<div class="qw-vlist">
  {#if title || action}
    <div class="qw-vlist-head">
      {#if title}<span class="qw-vlist-title">{title}</span>{/if}
      {#if action}
        <button type="button" class="qw-vlist-action" onclick={action.onclick}>
          {action.label}
        </button>
      {/if}
    </div>
  {/if}

  {#each versions as v (v.id)}
    <label
      class="qw-vrow"
      class:selected={mode === "single" && chosen.has(v.id)}
    >
      <input
        type={mode === "multi" ? "checkbox" : "radio"}
        name={mode === "multi" ? undefined : name}
        checked={chosen.has(v.id)}
        onchange={() => pick(v.id)}
      />
      <span class="qw-vrow-text">
        <span class="qw-vrow-line">
          <span class="qw-vrow-num" class:unpublished={!v.published}>
            v{v.number}
          </span>
          {#if v.meta}<span class="qw-vrow-meta">{v.meta}</span>{/if}
        </span>
        {#if v.from}<span class="qw-vrow-from">{v.from}</span>{/if}
      </span>
      {#if v.right}<span class="qw-vrow-right">{v.right}</span>{/if}
    </label>
  {/each}
</div>

<style>
  .qw-vlist {
    border: 1px solid var(--rule);
    border-radius: 6px;
    overflow: hidden;
    background: var(--surface);
  }
  .qw-vlist-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 7px 12px;
    background: var(--surface-sunk);
    border-bottom: 1px solid var(--rule-2);
  }
  .qw-vlist-title {
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .qw-vlist-action {
    border: none;
    background: none;
    padding: 0;
    font-family: var(--grotesk);
    font-size: 11.5px;
    color: var(--gold-deep);
    text-decoration: underline;
    text-underline-offset: 2px;
    cursor: pointer;
    white-space: nowrap;
  }
  .qw-vlist-action:focus-visible {
    outline: 2px solid var(--gold);
    outline-offset: 2px;
  }

  .qw-vrow {
    display: grid;
    grid-template-columns: 16px minmax(0, 1fr) auto;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border-bottom: 1px solid var(--rule-2);
    font-size: 13px;
    cursor: pointer;
    transition: background 0.1s;
  }
  .qw-vrow:last-child {
    border-bottom: none;
  }
  .qw-vrow:hover {
    background: var(--hover-overlay);
  }
  .qw-vrow.selected {
    background: light-dark(oklch(0.96 0.03 85), oklch(0.28 0.04 80));
  }
  .qw-vrow:focus-within {
    outline: 2px solid var(--gold);
    outline-offset: -2px;
  }
  .qw-vrow input {
    width: 15px;
    height: 15px;
    accent-color: var(--primary);
    margin: 0;
    cursor: pointer;
  }
  .qw-vrow-text {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }
  .qw-vrow-line {
    display: flex;
    align-items: baseline;
    gap: 8px;
    flex-wrap: wrap;
  }
  .qw-vrow-num {
    font-family: var(--mono);
    font-size: 12px;
    color: var(--fg);
  }
  .qw-vrow-num.unpublished {
    color: var(--fg-muted);
  }
  .qw-vrow-meta,
  .qw-vrow-from {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
  }
  .qw-vrow-from {
    font-size: 10.5px;
  }
  .qw-vrow-right {
    font-family: var(--mono);
    font-size: 11.5px;
    font-variant-numeric: tabular-nums;
    color: var(--fg-2);
    text-align: right;
    white-space: nowrap;
  }
</style>
