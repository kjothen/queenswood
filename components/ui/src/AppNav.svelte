<script>
  /* Sticky top bar for authenticated screens. Mirrors the brand chrome
     of Landing.svelte's marketing nav so post-signin surfaces share
     visual language with the landing page.

     v2 — Surfaces / type colors migrated from raw palette tokens
     (--ink, --paper) to semantic tokens (--fg, --surface-translucent,
     etc.) so the bar adapts to light / dark via tokens.css. ThemeToggle
     dropped in beside Sign out. */

  import Logo from "./Logo.svelte";
  import Wordmark from "./Wordmark.svelte";
  import ThemeToggle from "./ThemeToggle.svelte";

  let { user, onSignOut } = $props();
</script>

<nav class="nav">
  <div class="nav-inner">
    <a class="brand" href="/">
      <Logo variant="A" size={28} idPrefix="appnav" />
      <span class="wm"><Wordmark variant="grotesk" size={14} /></span>
    </a>
    <div class="spacer"></div>
    <div class="who">
      {#if user?.["avatar-url"]}
        <img src={user["avatar-url"]} alt="" class="avatar" />
      {/if}
      {#if user?.name}
        <span class="name">{user.name}</span>
      {/if}
      <ThemeToggle />
      <button type="button" class="btn line" onclick={onSignOut}>Sign out</button>
    </div>
  </div>
</nav>

<style>
  .nav {
    position: sticky;
    top: 0;
    z-index: 30;
    background: var(--surface-translucent);
    backdrop-filter: saturate(140%) blur(10px);
    border-bottom: 1px solid var(--rule-2);
  }
  .nav-inner {
    /* Full-width: brand pinned to the left edge, who-cluster pinned
       to the right. The earlier max-width capped the bar at 1280px
       which centered both ends inward on wide displays; the .app-shell
       grid below is full-width anyway, so the chrome should match. */
    padding: 14px 28px;
    display: flex;
    align-items: center;
    gap: 24px;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    color: inherit;
    text-decoration: none;
  }
  .brand .wm {
    display: inline-block;
  }
  .spacer {
    flex: 1;
  }
  .who {
    display: flex;
    align-items: center;
    gap: 12px;
    font-family: var(--grotesk);
  }
  .avatar {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: 1px solid var(--rule);
  }
  .name {
    font-size: 14px;
    color: var(--fg-2);
  }

  .btn {
    height: 32px;
    padding: 0 14px;
    display: inline-flex;
    align-items: center;
    border-radius: 6px;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 0.005em;
    border: 1px solid transparent;
    cursor: pointer;
    font-family: var(--grotesk);
    white-space: nowrap;
    transition:
      background 0.12s,
      border-color 0.12s,
      color 0.12s,
      transform 0.08s;
  }
  .btn:active {
    transform: translateY(0.5px);
  }
  .btn.line {
    border-color: var(--rule);
    color: var(--fg);
    background: transparent;
  }
  .btn.line:hover {
    background: var(--hover-overlay);
  }
</style>
