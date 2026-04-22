<script>
  import { list_tiers, replace_tier } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";

  let { showToast } = $props();

  let tiers = $state([]);
  let loading = $state(false);
  let error = $state(null);
  let expandedTier = $state(null);
  let activeTab = $state({});
  let editedTiers = $state({});
  let saving = $state({});

  function toggleExpanded(tierId) {
    if (expandedTier === tierId) {
      expandedTier = null;
    } else {
      expandedTier = tierId;
      if (!activeTab[tierId]) activeTab[tierId] = "policies";
      if (!editedTiers[tierId]) initEdits(tierId);
    }
  }

  function switchTab(tierId, tab) {
    activeTab[tierId] = tab;
  }

  function initEdits(tierId) {
    const tier = tiers.find(t => t["tier-id"] === tierId);
    if (!tier) return;
    editedTiers[tierId] = {
      policies: tier.policies.map(p => ({...p})),
      limits: tier.limits.map(l => ({...l}))
    };
  }

  function toggleEffect(tierId, idx) {
    const p = editedTiers[tierId].policies[idx];
    p.effect = p.effect === "allow" ? "deny" : "allow";
  }

  function updateLimitValue(tierId, idx, value) {
    editedTiers[tierId].limits[idx].value = parseInt(value) || 0;
  }

  function formatCapability(cap) {
    return (cap ?? "").replace(/^policy-capability-/, "").replace(/-/g, " ");
  }

  function formatLimitType(t) {
    return (t ?? "").replace(/^limit-type-/, "").replace(/-/g, " ");
  }

  function formatKind(kind) {
    if (!kind || typeof kind !== "object") return "";
    const entries = Object.entries(kind);
    if (entries.length === 0) return "";
    const [k, v] = entries[0];
    if (v && typeof v === "object") return formatKind(v);
    const cleanKey = k.replace(/-/g, " ");
    const cleanVal = String(v ?? "").replace(/^[a-z-]+-type-/, "");
    return `${cleanKey}: ${cleanVal}`;
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_tiers();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        tiers = res.body.tiers ?? [];
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      error = err.message;
    } finally {
      loading = false;
    }
  }

  async function saveTier(tierId) {
    const edits = editedTiers[tierId];
    if (!edits) return;
    const name = tiers.find(t => t["tier-id"] === tierId)?.name ?? tierId;
    saving[tierId] = true;
    try {
      const res = await replace_tier(tierId, edits.policies, edits.limits);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showToast?.({ type: "success", message: `Tier ${name} updated` });
        await load();
        initEdits(tierId);
      } else {
        showToast?.({ type: "warning", message: res.body?.detail ?? `HTTP ${res["http-status"]}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      delete saving[tierId];
    }
  }

  export function refresh() { load(); }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3zm0 5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V8zm1 4a1 1 0 0 0-1 1v1a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-1a1 1 0 0 0-1-1H3z"/></svg>
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
        <th>Name</th>
        <th>Tier ID</th>
        <th>Policies</th>
        <th>Limits</th>
        <th>Created</th>
        <th>Updated</th>
      </tr>
    </thead>
    <tbody>
      {#if tiers.length === 0 && !loading}
        <tr><td colspan="6" class="empty">No tiers</td></tr>
      {/if}
      {#each tiers as tier}
        <tr class="tier-row" onclick={() => toggleExpanded(tier["tier-id"])}>
          <td>
            <span class="chevron">{expandedTier === tier["tier-id"] ? "\u25BC" : "\u25B6"}</span>
            <span class="tier-badge">{tier.name}</span>
          </td>
          <td class="mono">{tier["tier-id"]}</td>
          <td>{tier.policies?.length ?? 0}</td>
          <td>{tier.limits?.length ?? 0}</td>
          <td title={tier["created-at"]}>{time_ago(tier["created-at"])}</td>
          <td title={tier["updated-at"]}>{time_ago(tier["updated-at"])}</td>
        </tr>
        {#if expandedTier === tier["tier-id"]}
          <tr class="detail-row">
            <td colspan="6">
              <div class="tab-bar">
                <button class="tab-btn" class:active={activeTab[tier["tier-id"]] === "policies"}
                        onclick={(e) => { e.stopPropagation(); switchTab(tier["tier-id"], "policies"); }}>
                  Policies
                </button>
                <button class="tab-btn" class:active={activeTab[tier["tier-id"]] === "limits"}
                        onclick={(e) => { e.stopPropagation(); switchTab(tier["tier-id"], "limits"); }}>
                  Limits
                </button>
                <button class="save-btn"
                        disabled={saving[tier["tier-id"]]}
                        onclick={(e) => { e.stopPropagation(); saveTier(tier["tier-id"]); }}>
                  {saving[tier["tier-id"]] ? "Saving..." : "Save"}
                </button>
              </div>

              {#if activeTab[tier["tier-id"]] === "policies"}
                {#if (editedTiers[tier["tier-id"]]?.policies ?? []).length === 0}
                  <div class="detail-empty">No policies</div>
                {:else}
                  <table class="detail-table">
                    <thead>
                      <tr>
                        <th>Capability</th>
                        <th>Effect</th>
                        <th>Reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each editedTiers[tier["tier-id"]].policies as policy, idx}
                        <tr>
                          <td class="capitalize">{formatCapability(policy.capability)}</td>
                          <td>
                            <button class="effect-toggle"
                                    class:allow={policy.effect === "allow"}
                                    class:deny={policy.effect === "deny"}
                                    onclick={(e) => { e.stopPropagation(); toggleEffect(tier["tier-id"], idx); }}>
                              {policy.effect === "allow" ? "Allow" : "Deny"}
                            </button>
                          </td>
                          <td class="muted">{policy.reason ?? ""}</td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                {/if}
              {:else if activeTab[tier["tier-id"]] === "limits"}
                {#if (editedTiers[tier["tier-id"]]?.limits ?? []).length === 0}
                  <div class="detail-empty">No limits</div>
                {:else}
                  <table class="detail-table">
                    <thead>
                      <tr>
                        <th>Type</th>
                        <th>Kind</th>
                        <th>Value</th>
                        <th>Reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each editedTiers[tier["tier-id"]].limits as limit, idx}
                        <tr>
                          <td class="capitalize">{formatLimitType(limit.type)}</td>
                          <td class="muted">{formatKind(limit.kind)}</td>
                          <td>
                            <input type="number" class="limit-input"
                                   value={limit.value ?? 0}
                                   onclick={(e) => e.stopPropagation()}
                                   oninput={(e) => updateLimitValue(tier["tier-id"], idx, e.target.value)} />
                          </td>
                          <td class="muted">{limit.reason ?? ""}</td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                {/if}
              {/if}
            </td>
          </tr>
        {/if}
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

  .mono { font-family: monospace; font-size: 0.8rem; }

  .tier-row { cursor: pointer; }
  .tier-row:hover { background: var(--bg-hover); }

  .chevron {
    display: inline-block;
    width: 1.2em;
    font-size: 0.7rem;
    color: var(--text-muted);
  }

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

  .detail-row { background: var(--bg-secondary); }
  .detail-row td { padding: 1rem; }
  .detail-empty { color: var(--text-faint); font-size: 0.85rem; padding: 0.5rem 0; }

  .tab-bar {
    display: flex;
    gap: 0.25rem;
    margin-bottom: 0.75rem;
    align-items: center;
  }

  .tab-btn {
    padding: 0.3rem 0.8rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    background: var(--bg);
    color: var(--text-muted);
    font-size: 0.8rem;
    cursor: pointer;
  }

  .tab-btn.active {
    background: var(--bg-secondary);
    color: var(--text);
    font-weight: 600;
    border-color: var(--text-muted);
  }

  .save-btn {
    margin-left: auto;
    padding: 0.3rem 0.8rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .save-btn:disabled { opacity: 0.6; cursor: not-allowed; }
  .save-btn:not(:disabled):hover { background: #15803d; }

  .detail-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
  .detail-table th { background: transparent; font-size: 0.75rem; }
  .detail-table td { padding: 0.4rem 0.6rem; }

  .capitalize { text-transform: capitalize; }
  .muted { color: var(--text-muted); font-size: 0.85rem; }

  .effect-toggle {
    padding: 0.2rem 0.6rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    cursor: pointer;
    width: 60px;
    text-align: center;
  }

  .effect-toggle.allow { background: #dcfce7; color: #166534; border-color: #86efac; }
  .effect-toggle.deny { background: #fee2e2; color: #991b1b; border-color: #fca5a5; }

  .limit-input {
    width: 80px;
    padding: 0.25rem 0.4rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.85rem;
    background: var(--bg-input);
    color: var(--text);
    text-align: right;
  }
</style>
