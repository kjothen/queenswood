<script>
  let { onSubmit } = $props();
  let token = $state("");
</script>

<div class="login">
  <div class="card">
    <h1>Queenswood</h1>
    <p>Paste your admin API key to begin.</p>
    <input
      type="password"
      placeholder="Admin API key"
      bind:value={token}
      onkeydown={(e) => { if (e.key === "Enter" && token) onSubmit(token); }}
    />
    <button onclick={() => onSubmit(token)} disabled={!token}>Continue</button>
    <details>
      <summary>How to find your key</summary>
      <p>The helm install command printed it. To recover it from the cluster:</p>
      <pre><code>kubectl -n queenswood get secret queenswood-admin-api-key \
  -o jsonpath='&lbrace;.data.MONO_ADMIN_API_KEY&rbrace;' | base64 -d</code></pre>
    </details>
  </div>
</div>

<style>
  .login {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    background: var(--bg);
    color: var(--text);
    font-family: system-ui, -apple-system, sans-serif;
  }

  .card {
    width: min(420px, 90vw);
    padding: 2rem;
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 8px;
  }

  h1 {
    margin: 0 0 0.5rem;
    font-size: 1.5rem;
  }

  p {
    margin: 0 0 1rem;
    color: var(--text-muted);
  }

  input {
    width: 100%;
    padding: 0.5rem 0.75rem;
    margin-bottom: 0.75rem;
    background: var(--bg-input);
    color: var(--text);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font: inherit;
    box-sizing: border-box;
  }

  button {
    width: 100%;
    padding: 0.6rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font: inherit;
    cursor: pointer;
  }

  button:disabled {
    background: var(--bg-hover);
    color: var(--text-faint);
    cursor: not-allowed;
  }

  details {
    margin-top: 1rem;
    color: var(--text-muted);
    font-size: 0.9rem;
  }

  summary {
    cursor: pointer;
  }

  pre {
    margin: 0.5rem 0 0;
    padding: 0.5rem;
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 4px;
    overflow-x: auto;
    font-size: 0.85rem;
  }
</style>
