<script>
  import { onMount } from "svelte";
  import { onboard } from "./api.mjs";

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

<div class="onboarding">
  <div class="card">
    <h1>Welcome to Queenswood</h1>
    <p>Name your organization to finish setting up your account.</p>
    <form onsubmit={(e) => { e.preventDefault(); submit(); }}>
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
      <button type="submit" disabled={!organization_name.trim() || submitting}>
        {submitting ? "Creating…" : "Continue"}
      </button>
    </form>
    <button type="button" class="link" onclick={onSignOut}>Sign out</button>
  </div>
</div>

<style>
  .onboarding {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    background: #f9fafb;
    color: #111827;
    font-family: system-ui, -apple-system, sans-serif;
  }

  .card {
    width: min(420px, 90vw);
    padding: 2rem;
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
  }

  h1 {
    margin: 0 0 0.5rem;
    font-size: 1.5rem;
  }

  p {
    margin: 0 0 1.5rem;
    color: #6b7280;
  }

  label {
    display: block;
    font-size: 0.85rem;
    color: #374151;
    margin-bottom: 0.4rem;
  }

  input {
    width: 100%;
    padding: 0.6rem 0.75rem;
    margin-bottom: 1rem;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    font: inherit;
    box-sizing: border-box;
  }

  button[type="submit"] {
    width: 100%;
    padding: 0.7rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font: inherit;
    font-weight: 500;
    cursor: pointer;
  }

  button[type="submit"]:disabled {
    background: #93c5fd;
    cursor: not-allowed;
  }

  .link {
    display: block;
    margin: 1rem auto 0;
    background: none;
    border: none;
    color: #6b7280;
    font-size: 0.85rem;
    cursor: pointer;
    text-decoration: underline;
  }

  .error {
    margin: 0 0 0.75rem;
    color: #b91c1c;
    font-size: 0.9rem;
  }
</style>
