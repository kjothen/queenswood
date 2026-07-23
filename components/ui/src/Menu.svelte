<script>
  /* Menu — a small popover action menu (the row "⋯" kebab).

     Positioned against the trigger's bounding rect, right-aligned and
     6px below it, flipping above when it would overflow the viewport
     bottom and clamped 8px off the edges. `position: fixed` lets it
     escape a table's overflow clip. Closes on backdrop click, Esc,
     resize, and scroll.

         <Menu anchor={btnRect} onClose={() => menuOpen = false}
           items={[
             { label: "Run now", onClick: run, disabled: running },
             { label: "Edit schedule", onClick: edit, divider: true },
             { label: job.enabled ? "Pause" : "Resume", onClick: toggle },
           ]} />

     `anchor` is a DOMRect (from getBoundingClientRect()). */

  let { anchor, items = [], onClose } = $props();

  let el = $state(null);
  let left = $state(0);
  let top = $state(0);
  let placed = $state(false);

  function close() {
    onClose?.();
  }

  function onKey(e) {
    if (e.key === "Escape") close();
  }

  $effect(() => {
    // Re-place whenever the anchor changes; read size after the node is
    // in the DOM so offsetWidth/Height are real.
    if (el && anchor) {
      const mw = el.offsetWidth;
      const mh = el.offsetHeight;
      let l = anchor.right - mw;
      let t = anchor.bottom + 6;
      if (t + mh > window.innerHeight - 8) t = anchor.top - mh - 6;
      left = Math.max(8, l);
      top = Math.max(8, t);
      placed = true;
    }

    window.addEventListener("keydown", onKey);
    window.addEventListener("resize", close);
    // A fixed popover can't follow a scroll, so dismiss instead.
    window.addEventListener("scroll", close, true);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("resize", close);
      window.removeEventListener("scroll", close, true);
    };
  });

  function select(item) {
    if (item.disabled) return;
    close();
    item.onClick?.();
  }
</script>

<div class="menu-backdrop" onclick={close} aria-hidden="true"></div>
<div
  class="menu"
  bind:this={el}
  role="menu"
  tabindex="-1"
  style:left="{left}px"
  style:top="{top}px"
  style:opacity={placed ? 1 : 0}
>
  {#each items as item, i (item.label)}
    {#if item.divider && i > 0}<hr />{/if}
    <button
      type="button"
      role="menuitem"
      disabled={item.disabled}
      class:danger={item.danger}
      onclick={() => select(item)}
    >
      {item.label}
    </button>
  {/each}
</div>

<style>
  .menu-backdrop { position: fixed; inset: 0; z-index: 59; background: transparent; }
  .menu {
    position: fixed;
    min-width: 188px;
    background: var(--surface-raised);
    border: 1px solid var(--rule);
    border-radius: 9px;
    box-shadow: light-dark(
      0 14px 36px -12px rgba(20, 15, 10, 0.28),
      0 14px 36px -12px rgba(0, 0, 0, 0.6)
    );
    padding: 6px;
    z-index: 60;
  }
  .menu button {
    display: flex;
    align-items: center;
    gap: 9px;
    width: 100%;
    padding: 8px 10px;
    border-radius: 6px;
    background: transparent;
    border: none;
    text-align: left;
    font-family: var(--grotesk);
    font-size: 13px;
    color: var(--fg-2);
    cursor: pointer;
  }
  .menu button:hover { background: var(--hover-overlay); color: var(--fg); }
  .menu button:disabled { opacity: 0.45; cursor: not-allowed; }
  .menu button.danger { color: var(--danger); }
  .menu hr { border: none; border-top: 1px solid var(--rule-2); margin: 6px 4px; }
</style>
