<script>
  import { onMount } from "svelte";
  import { onboard } from "./api.mjs";
  import { AppNav } from "@queenswood/bank-ui";

  let { onComplete, onSignOut, defaultName = "" } = $props();
  // Read `defaultName` inside onMount, not at module scope: Svelte 5
  // would otherwise snapshot the prop and never see later updates,
  // and the static analysis fires `state_referenced_locally`.
  let organization_name = $state("");
  let submitting = $state(false);
  let error_message = $state(null);

  onMount(() => {
    organization_name = defaultName;
  });

  async function submit() {
    if (!organization_name.trim() || submitting) return;
    submitting = true;
    error_message = null;
    const { status, body } = await onboard(organization_name.trim());
    submitting = false;
    if (status === 201) {
      onComplete(body);
    } else {
      error_message = body?.detail ?? `Onboarding failed (status ${status})`;
    }
  }
</script>

<div class="page">
  <AppNav {onSignOut} />

  <main class="wrap">
    <span class="eyebrow">Console · onboarding</span>
    <h2>Welcome to <em>Queenswood.</em></h2>
    <p class="lede">Name your organization to finish setting up your account.</p>

    <form class="card" onsubmit={(e) => { e.preventDefault(); submit(); }}>
      <label for="org">Organization name</label>
      <input
        id="org"
        type="text"
        bind:value={organization_name}
        placeholder="Acme Bank"
        disabled={submitting}
        required
      />
      {#if error_message}
        <p class="error">{error_message}</p>
      {/if}
      <button
        type="submit"
        class="btn solid"
        disabled={!organization_name.trim() || submitting}
      >
        {submitting ? "Creating…" : "Continue"}
      </button>
    </form>
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
    max-width: 520px;
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
    font-size: 44px;
    line-height: 1.05;
    letter-spacing: -0.008em;
    margin: 14px 0 14px;
    text-wrap: pretty;
  }
  h2 em {
    font-style: italic;
    color: var(--gold-deep);
    font-weight: 500;
  }

  .lede {
    font-size: 16px;
    line-height: 1.55;
    color: var(--ink-2);
    margin: 0 0 28px;
  }

  .card {
    padding: 26px;
    background: var(--paper);
    border: 1px solid var(--rule);
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  label {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    color: var(--gold-deep);
  }

  input {
    padding: 12px 14px;
    margin-bottom: 8px;
    border: 1px solid var(--rule);
    border-radius: 6px;
    background: var(--bone);
    color: var(--ink);
    font: inherit;
    font-family: var(--grotesk);
    font-size: 15px;
    box-sizing: border-box;
    transition: border-color 0.12s;
  }
  input:focus {
    outline: none;
    border-color: var(--gold-deep);
  }

  .btn {
    height: 44px;
    padding: 0 18px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    border-radius: 6px;
    font-size: 14px;
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
  .btn.solid {
    background: var(--ink);
    color: var(--bone);
  }
  .btn.solid:hover {
    background: #2a2622;
  }
  .btn:disabled {
    background: var(--muted);
    color: var(--bone);
    cursor: not-allowed;
    opacity: 0.6;
  }

  .error {
    margin: 0;
    color: oklch(0.55 0.18 28);
    font-size: 13px;
  }
</style>
