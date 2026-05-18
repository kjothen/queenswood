<script>
  let { onSignIn } = $props();
</script>

<div class="signin">
  <div class="card">
    <h1>Queenswood</h1>
    <p>Sign in with your Google account to continue.</p>
    <button onclick={onSignIn}>
      <span class="g">G</span>
      Sign in with Google
    </button>
  </div>
</div>

<style>
  .signin {
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
    text-align: center;
  }

  h1 {
    margin: 0 0 0.5rem;
    font-size: 1.5rem;
  }

  p {
    margin: 0 0 1.5rem;
    color: #6b7280;
  }

  button {
    width: 100%;
    padding: 0.75rem;
    background: #ffffff;
    color: #111827;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    font: inherit;
    font-weight: 500;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.6rem;
  }

  button:hover {
    background: #f3f4f6;
  }

  .g {
    display: inline-block;
    width: 1.5rem;
    height: 1.5rem;
    line-height: 1.5rem;
    background: #4285F4;
    color: white;
    border-radius: 50%;
    font-weight: 700;
    font-size: 0.9rem;
  }
</style>
