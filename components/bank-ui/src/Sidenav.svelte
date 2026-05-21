<script>
  /* Sidenav — left-rail navigation container.

     Composes with SidenavGroup (sectioning) and SidenavItem (links).
     The seam on the right edge is a drag handle: drag left past ~130px
     to snap to icon-only (60px), drag right to snap back to 220px.
     Double-click the seam to toggle directly. State persists in
     localStorage under "queenswood.sidenav.collapsed".

     The component publishes its current width to `--sidenav-w` on
     <html>, so the consumer's layout can do:

         .app-shell {
           display: grid;
           grid-template-columns: var(--sidenav-w, 220px) minmax(0, 1fr);
         }

     During drag, <html> gets a `sidenav-dragging` class — the matching
     :global rule below kills the shell's transition so the layout
     follows the pointer crisply. */

  import { onMount } from "svelte";

  let {
    top = 57,
    expandedWidth = 220,
    collapsedWidth = 60,
    children,
  } = $props();

  const STORAGE_KEY = "queenswood.sidenav.collapsed";
  const SNAP_AT = 130;

  let sidenavEl = $state(null);
  let collapsed = $state(false);
  let dragging = $state(false);
  let dragState = null;

  function applyWidth(w) {
    document.documentElement.style.setProperty("--sidenav-w", `${w}px`);
  }

  function setCollapsed(c, persist = true) {
    collapsed = c;
    applyWidth(c ? collapsedWidth : expandedWidth);
    if (persist) {
      try { localStorage.setItem(STORAGE_KEY, c ? "1" : "0"); } catch {}
    }
  }

  onMount(() => {
    let saved = false;
    try { saved = localStorage.getItem(STORAGE_KEY) === "1"; } catch {}
    setCollapsed(saved, false);
  });

  function onMouseDown(e) {
    e.preventDefault();
    dragState = { startX: e.clientX, startW: sidenavEl.offsetWidth };
    dragging = true;
    document.documentElement.classList.add("sidenav-dragging");
    document.body.style.cursor = "ew-resize";
    document.body.style.userSelect = "none";
  }
  function onMouseMove(e) {
    if (!dragState) return;
    const newW = Math.max(50, Math.min(320, dragState.startW + (e.clientX - dragState.startX)));
    applyWidth(newW);
    collapsed = newW < SNAP_AT;
  }
  function onMouseUp() {
    if (!dragState) return;
    dragState = null;
    dragging = false;
    document.documentElement.classList.remove("sidenav-dragging");
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
    setCollapsed(sidenavEl.offsetWidth < SNAP_AT);
  }
  function onDblClick() {
    setCollapsed(!collapsed);
  }

  $effect(() => {
    if (!dragging) return;
    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
    return () => {
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
    };
  });
</script>

<aside
  class="sidenav"
  class:collapsed
  class:dragging
  bind:this={sidenavEl}
  style:top="{top}px"
  style:height="calc(100vh - {top}px)"
>
  {@render children?.()}
  <div
    class="sidenav-resize"
    class:dragging
    aria-hidden="true"
    title="Drag to resize · double-click to toggle"
    onmousedown={onMouseDown}
    ondblclick={onDblClick}
  ></div>
</aside>

<style>
  .sidenav {
    position: sticky;
    align-self: start;
    width: var(--sidenav-w, 220px);
    background: var(--surface-sunk);
    border-right: 1px solid var(--rule-2);
    padding: 20px 12px;
    display: flex;
    flex-direction: column;
    gap: 24px;
    transition: width 0.12s ease, padding 0.12s ease;
  }
  .sidenav.dragging { transition: none; }

  /* Shared styles for descendants from SidenavGroup / SidenavItem.
     :global is intentional — these components live in separate files
     but share one visual contract. */
  :global(.sidenav-group) {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  :global(.sidenav-grouptitle) {
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--fg-muted);
    padding: 4px 12px 8px;
  }
  :global(.sidenav-item) {
    position: relative;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    background: transparent;
    border: none;
    border-radius: 6px;
    color: var(--fg-muted);
    font-family: var(--grotesk);
    font-size: 13px;
    font-weight: 500;
    text-align: left;
    cursor: pointer;
    transition: background 0.1s, color 0.1s;
    text-decoration: none;
  }
  :global(.sidenav-item:hover) {
    background: var(--hover-overlay);
    color: var(--fg);
  }
  :global(.sidenav-item[aria-current="page"]) {
    background: light-dark(oklch(0.94 0.025 145), oklch(0.24 0.04 145));
    color: var(--fg);
  }
  :global(.sidenav-item[aria-current="page"]::before) {
    content: "";
    position: absolute;
    left: 0;
    top: 6px;
    bottom: 6px;
    width: 2.5px;
    background: var(--pine-4);
    border-radius: 0 2px 2px 0;
  }
  :global(.sidenav-item svg) {
    width: 16px;
    height: 16px;
    stroke: currentColor;
    fill: none;
    stroke-width: 1.5;
    stroke-linecap: round;
    stroke-linejoin: round;
    flex: 0 0 auto;
  }
  :global(.sidenav-label) { display: inline-block; }

  /* Collapsed state — icons only */
  .sidenav.collapsed { padding: 20px 8px; gap: 14px; }
  .sidenav.collapsed :global(.sidenav-grouptitle) { display: none; }
  .sidenav.collapsed :global(.sidenav-label) { display: none; }
  .sidenav.collapsed :global(.sidenav-item) {
    padding: 8px 0;
    justify-content: center;
  }

  /* Drag handle */
  .sidenav-resize {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 5px;
    margin-right: -2px;
    cursor: ew-resize;
    background: transparent;
    transition: background 0.12s;
    z-index: 5;
  }
  .sidenav-resize:hover { background: var(--rule); }
  .sidenav-resize.dragging { background: var(--gold-deep); }

  /* Kill the shell's grid-template-columns transition during drag so
     the layout follows the pointer 1:1. Consumer must use `.app-shell`
     as the shell class for this to bite. */
  :global(:root.sidenav-dragging .app-shell) {
    transition: none !important;
  }
</style>
