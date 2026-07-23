<script>
  /* SearchField — a search input with a leading magnifier, a trailing
     clear (✕) that appears only when there's a value, and a gold focus
     ring. `size="sm"` is the compact 34px variant (the default is 40px).

     Two-way bound:
       <SearchField bind:value placeholder="Search…" />
       <SearchField bind:value size="sm" onclear={() => …} /> */

  let {
    value = $bindable(""),
    placeholder = "Search…",
    size = "md",
    ariaLabel = "Search",
    onclear,
    onkeydown,
  } = $props();

  let input;

  function clear() {
    value = "";
    onclear?.();
    input?.focus();
  }
</script>

<div class="search-field" class:sm={size === "sm"} class:has-value={value.length > 0}>
  <svg
    class="search-ico"
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
    bind:this={input}
    type="search"
    bind:value
    {placeholder}
    autocomplete="off"
    aria-label={ariaLabel}
    {onkeydown}
  />
  <button type="button" class="search-clear" aria-label="Clear search" onclick={clear}>
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
      <path d="M4 4 L12 12 M12 4 L4 12" />
    </svg>
  </button>
</div>

<style>
  .search-field {
    position: relative;
    display: block;
  }
  .search-ico {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 15px;
    height: 15px;
    color: var(--fg-muted);
    pointer-events: none;
  }
  input {
    height: 40px;
    width: 100%;
    padding: 0 36px;
    border-radius: 6px;
    border: 1px solid var(--rule);
    background: var(--surface);
    color: var(--fg);
    font: inherit;
    font-size: 14px;
    transition:
      border-color 0.12s,
      background 0.12s;
  }
  input::placeholder {
    color: var(--fg-muted);
  }
  input:hover {
    border-color: light-dark(rgba(20, 15, 10, 0.18), rgba(244, 241, 234, 0.2));
  }
  input:focus {
    outline: 2px solid var(--gold);
    outline-offset: -1px;
    border-color: var(--gold);
  }
  input[type="search"]::-webkit-search-decoration,
  input[type="search"]::-webkit-search-cancel-button {
    -webkit-appearance: none;
  }
  .search-clear {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    width: 24px;
    height: 24px;
    border: none;
    border-radius: 5px;
    background: transparent;
    color: var(--fg-muted);
    cursor: pointer;
    display: none;
    align-items: center;
    justify-content: center;
  }
  .search-clear:hover {
    background: var(--hover-overlay);
    color: var(--fg);
  }
  .has-value .search-clear {
    display: inline-flex;
  }
  .sm input {
    height: 34px;
    font-size: 13px;
  }
</style>
