<script>
  let { onSignIn } = $props();
</script>

<div class="signin">
  <div class="card">
    <h1>Queenswood operator console</h1>
    <p>Sign in with your operator account to continue.</p>
    <button onclick={onSignIn}>Sign in</button>
  </div>
</div>

<style>
  .signin {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    background: var(--bg, #f9fafb);
    color: var(--text, #111827);
    font-family: system-ui, -apple-system, sans-serif;
  }

  .card {
    width: min(420px, 90vw);
    padding: 2rem;
    background: var(--bg-secondary, #ffffff);
    border: 1px solid var(--border, #e5e7eb);
    border-radius: 8px;
    text-align: center;
  }

  h1 {
    margin: 0 0 0.5rem;
    font-size: 1.5rem;
  }

  p {
    margin: 0 0 1.5rem;
    color: var(--text-muted, #6b7280);
  }

  button {
    width: 100%;
    padding: 0.75rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font: inherit;
    font-weight: 500;
    cursor: pointer;
  }

  button:hover {
    background: #1d4ed8;
  }
</style>
