<script>
  import { create_account } from "./api.mjs";

  let customerId = $state("customer-id");
  let name = $state("Firstname Lastname");
  let currency = $state("GBP");
  let result = $state(null);
  let httpStatus = $state(null);
  let submitting = $state(false);

  async function handleSubmit(e) {
    e.preventDefault();
    submitting = true;
    result = null;
    httpStatus = null;
    try {
      const res = await create_account({
        "customer-id": customerId,
        "name": name,
        "currency": currency,
      });
      httpStatus = res["http-status"];
      result = res.body;
    } catch (err) {
      httpStatus = 0;
      result = { status: "ERROR", error: err.message };
    } finally {
      submitting = false;
    }
  }
</script>

<form onsubmit={handleSubmit}>
  <label>
    Customer ID
    <input type="text" bind:value={customerId} required />
  </label>

  <label>
    Name
    <input type="text" bind:value={name} required />
  </label>

  <label>
    Currency
    <select bind:value={currency}>
      <option>USD</option>
      <option>EUR</option>
      <option>GBP</option>
      <option>CHF</option>
      <option>JPY</option>
    </select>
  </label>

  <button type="submit" disabled={submitting}>
    {submitting ? "Creating..." : "Create Account"}
  </button>
</form>

{#if result}
  <div class="result"
       class:success={httpStatus >= 200 && httpStatus < 300}
       class:warning={httpStatus >= 400 && httpStatus < 500}
       class:error={httpStatus >= 500 || httpStatus === 0}>
    <pre>{JSON.stringify(result, null, 2)}</pre>
  </div>
{/if}

<style>
  form {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-weight: 500;
  }

  input,
  select {
    padding: 0.5rem;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 1rem;
  }

  button {
    padding: 0.6rem 1.2rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 1rem;
    cursor: pointer;
  }

  button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .result {
    margin-top: 1.5rem;
    padding: 1rem;
    border-radius: 4px;
  }

  .success {
    background: #dcfce7;
    border: 1px solid #86efac;
  }

  .warning {
    background: #fef9c3;
    border: 1px solid #fde047;
  }

  .error {
    background: #fee2e2;
    border: 1px solid #fca5a5;
  }

  pre {
    margin-top: 0.5rem;
    font-size: 0.85rem;
    white-space: pre-wrap;
    word-break: break-all;
  }
</style>
