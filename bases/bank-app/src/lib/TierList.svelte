<script>
  import { list_tiers } from "./api.mjs";
  import { onMount } from "svelte";

  let { showToast } = $props();

  let tiers = $state([]);
  let loading = $state(false);
  let error = $state(null);

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_tiers();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        tiers = res.body.tiers ?? [];
      } else {
        error = res.body?.detail ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      error = err.message;
    } finally {
      loading = false;
    }
  }

  export function refresh() { load(); }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M1 3l7-2 7 2v1l-7 2-7-2V3zm0 4l7 2 7-2v1l-7 2-7-2V7zm0 4l7 2 7-2v1l-7 2-7-2v-1z"/></svg>
      Tiers
    </h2>
    <button class="refresh" onclick={() => load()} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>Tier</th>
        <th>Description</th>
      </tr>
    </thead>
    <tbody>
      {#if tiers.length === 0 && !loading}
        <tr><td colspan="2" class="empty">No tiers</td></tr>
      {/if}
      {#each tiers as t}
        <tr>
          <td>
            <span class="tier-badge">{t.tier}</span>
          </td>
          <td class="muted">{t.description ?? ""}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</section>

<style>
  section { margin-top: 0; }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }

  h2 {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 0.5rem;
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

  .refresh:disabled { opacity: 0.6; cursor: not-allowed; }

  .error-msg {
    background: var(--bg-error);
    border: 1px solid var(--border-error);
    padding: 0.75rem;
    border-radius: 4px;
    margin-bottom: 1rem;
  }

  table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }

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

  .empty { text-align: center; color: var(--text-faint); padding: 1.5rem; }

  .tier-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
    background: #f3e8ff;
    color: #6b21a8;
    text-transform: capitalize;
  }

  .muted { color: var(--text-muted); font-size: 0.85rem; }
</style>
