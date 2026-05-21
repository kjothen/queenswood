<script>
  import { AppNav } from "@queenswood/bank-ui";

  let { user, memberships, onSignOut } = $props();
  const organization = $derived(memberships?.[0]);
</script>

<div class="page">
  <AppNav {user} {onSignOut} />

  <main class="wrap">
    <span class="eyebrow">Console · home</span>
    <h2>Welcome, <em>{user?.name ?? "friend"}.</em></h2>

    <div class="card">
      {#if organization}
        <span class="cap-label">Organization</span>
        <p class="org-id">{organization["organization-id"]}</p>
      {:else}
        <span class="cap-label">Organization</span>
        <p class="muted">No organization yet.</p>
      {/if}
    </div>
  </main>
</div>

<style>
  .page {
    min-height: 100vh;
    background: var(--paper);
    color: var(--ink);
    font-family: var(--grotesk);
  }

  .wrap {
    max-width: 1280px;
    margin: 0 auto;
    padding: 64px 32px;
  }

  .eyebrow {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--muted);
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  .eyebrow::before {
    content: "";
    width: 18px;
    height: 1px;
    background: var(--gold-deep);
  }

  h2 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 48px;
    line-height: 1.05;
    letter-spacing: -0.008em;
    margin: 14px 0 36px;
    max-width: 18ch;
    text-wrap: pretty;
  }
  h2 em {
    font-style: italic;
    color: var(--gold-deep);
    font-weight: 500;
  }

  .card {
    padding: 26px;
    background: var(--paper);
    border: 1px solid var(--rule);
    border-radius: 12px;
    max-width: 640px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .cap-label {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--gold-deep);
  }

  .org-id {
    margin: 0;
    font-family: var(--mono);
    font-size: 14px;
    color: var(--ink);
  }

  .muted {
    margin: 0;
    color: var(--muted);
    font-size: 14px;
  }
</style>
