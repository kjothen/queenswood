<script>
  /* Sticky top bar for authenticated screens (Dashboard, Onboarding).
     Mirrors the brand chrome of Landing.svelte's marketing nav so the
     post-signin surfaces share visual language with the landing page. */

  import Logo from "./Logo.svelte";
  import Wordmark from "./Wordmark.svelte";

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
      <button type="button" class="btn line" onclick={onSignOut}>Sign out</button>
    </div>
  </div>
</nav>

<style>
  .nav {
    position: sticky;
    top: 0;
    z-index: 30;
    background: rgba(251, 249, 244, 0.86);
    backdrop-filter: saturate(140%) blur(10px);
    border-bottom: 1px solid var(--rule-2);
  }
  .nav-inner {
    max-width: 1280px;
    margin: 0 auto;
    padding: 14px 32px;
    display: flex;
    align-items: center;
    gap: 28px;
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
    color: var(--ink-2);
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
    color: var(--ink);
    background: transparent;
  }
  .btn.line:hover {
    background: rgba(20, 15, 10, 0.05);
  }
</style>
