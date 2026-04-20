<script>
  import { list_payee_checks } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";

  onMount(() => load());

  let checks = $state([]);
  let links = $state({});
  let loading = $state(false);
  let error = $state(null);
  let currentQuery = $state(null);

  function queryFromLink(url) {
    const idx = url.indexOf("?");
    return idx >= 0 ? url.substring(idx + 1) : null;
  }

  export async function load(queryString) {
    loading = true;
    error = null;
    currentQuery = queryString ?? null;
    try {
      const res = await list_payee_checks(queryString);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        checks = res.body.items ?? [];
        links = res.body.links ?? {};
      } else {
        error = res.body?.detail ?? `HTTP ${res["http-status"]}`;
        checks = [];
        links = {};
      }
    } catch (err) {
      error = err.message;
      checks = [];
      links = {};
    } finally {
      loading = false;
    }
  }

  function matchClass(result) {
    switch (result) {
      case "match": return "match";
      case "close-match": return "close-match";
      case "no-match": return "no-match";
      default: return "unavailable";
    }
  }

  function matchLabel(result) {
    switch (result) {
      case "match": return "Match";
      case "close-match": return "Close match";
      case "no-match": return "No match";
      default: return "Unavailable";
    }
  }
</script>

<section>
  <div class="header">
    <h2>CoP Checks</h2>
    <button class="refresh" onclick={() => load(currentQuery)} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  {#if checks.length === 0 && !loading}
    <p class="empty">No payee checks found.</p>
  {:else}
    <table>
      <thead>
        <tr>
          <th>Result</th>
          <th>Creditor</th>
          <th>Account</th>
          <th>Created</th>
        </tr>
      </thead>
      <tbody>
        {#each checks as check}
          <tr>
            <td>
              <span class="result-badge {matchClass(check.result?.['match-result'])}">
                {matchLabel(check.result?.["match-result"])}
              </span>
            </td>
            <td>{check.request?.["creditor-name"] ?? ""}</td>
            <td class="mono">
              {check.request?.account?.["sort-code"] ?? ""}
              {check.request?.account?.["account-number"] ?? ""}
            </td>
            <td class="muted">{time_ago(check["created-at"])}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}

  <div class="pagination">
    <button
      disabled={!links.prev || loading}
      onclick={() => load(queryFromLink(links.prev))}
    >
      Prev
    </button>
    <button
      disabled={!links.next || loading}
      onclick={() => load(queryFromLink(links.next))}
    >
      Next
    </button>
  </div>
</section>

<style>
  section {
    margin-top: 2rem;
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }

  h2 {
    margin: 0;
  }

  .refresh {
    padding: 0.4rem 0.8rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .refresh:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .error-msg {
    background: var(--bg-error);
    border: 1px solid var(--border-error);
    padding: 0.75rem;
    border-radius: 4px;
    margin-bottom: 1rem;
  }

  .empty {
    text-align: center;
    color: var(--text-faint);
    padding: 1.5rem;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  th, td {
    text-align: left;
    padding: 0.5rem 0.6rem;
    border-bottom: 1px solid var(--border);
  }

  th {
    background: var(--bg-secondary);
    font-weight: 600;
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--text-muted);
  }

  .mono {
    font-family: monospace;
    font-size: 0.8rem;
  }

  .muted {
    color: var(--text-muted);
    font-size: 0.85rem;
  }

  .result-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
  }

  .result-badge.match {
    background: #dcfce7;
    color: #166534;
  }

  .result-badge.close-match {
    background: #fef9c3;
    color: #854d0e;
  }

  .result-badge.no-match {
    background: #fee2e2;
    color: #991b1b;
  }

  .result-badge.unavailable {
    background: var(--bg-secondary);
    color: var(--text-muted);
  }

  .pagination {
    display: flex;
    justify-content: center;
    gap: 0.5rem;
    margin-top: 1rem;
  }

  .pagination button {
    padding: 0.4rem 1rem;
    background: var(--bg-pagination);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85rem;
  }

  .pagination button:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .pagination button:not(:disabled):hover {
    background: var(--bg-hover);
  }
</style>
