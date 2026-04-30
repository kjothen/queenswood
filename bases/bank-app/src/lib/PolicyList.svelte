<script>
  import { list_policies } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";

  let { showToast } = $props();

  let policies = $state([]);
  let loading = $state(false);
  let error = $state(null);
  let expandedPolicy = $state(null);
  let activeTab = $state({});

  function toggleExpanded(policyId) {
    if (expandedPolicy === policyId) {
      expandedPolicy = null;
    } else {
      expandedPolicy = policyId;
      if (!activeTab[policyId]) activeTab[policyId] = "capabilities";
    }
  }

  function switchTab(policyId, tab) {
    activeTab[policyId] = tab;
  }

  // Strip protojure prefixes ("balance-action-apply" -> "apply",
  // "organization-type-customer" -> "customer", "effect-allow" -> "allow")
  // and collapse hyphens for display.
  function stripPrefix(v) {
    if (v == null) return "";
    const s = String(v);
    // Drop everything up to and including the last "-<word>-" segment when
    // it matches a known shape, else just drop a leading "<x>-" prefix.
    return s.replace(/^[a-z]+(?:-[a-z]+)*-/, "");
  }

  function pretty(v) {
    return stripPrefix(v).replace(/-/g, " ");
  }

  // protojure's representation of an unset optional proto field —
  // nil for missing message-typed fields, or a `:*-unknown` keyword
  // for an enum at its proto2 zero default.
  function isUnset(v) {
    if (v == null) return true;
    if (typeof v === "string" && v.endsWith("-unknown")) return true;
    return false;
  }

  // Returns the oneof variant key + body for a single-entry kind map,
  // e.g. {balance: {action: ...}} -> ["balance", {action: ...}].
  function variant(kindMap) {
    if (!kindMap || typeof kindMap !== "object") return [null, null];
    const keys = Object.keys(kindMap);
    if (keys.length === 0) return [null, null];
    return [keys[0], kindMap[keys[0]]];
  }

  // Build a brief one-line summary of the filter's set fields. For
  // nested oneof values (e.g. BalanceLimitFilter.kind = {computed:
  // {name: "available"}}) we recurse one level so the meaningful
  // body shows up as "kind=computed(name: available)".
  function filterSummary(filter) {
    if (!filter || typeof filter !== "object") return "";
    const parts = [];
    for (const [k, v] of Object.entries(filter)) {
      if (isUnset(v)) continue;
      if (v && typeof v === "object" && !Array.isArray(v)) {
        const [innerK, innerV] = variant(v);
        if (innerK) {
          const inner = filterSummary(innerV);
          parts.push(inner ? `${k}=${innerK}(${inner})` : `${k}=${innerK}`);
          continue;
        }
      }
      parts.push(`${k}: ${stripPrefix(v)}`);
    }
    return parts.join(", ");
  }

  function filtersDisplay(filters) {
    const xs = filters ?? [];
    if (xs.length === 0) return "any";
    const head = filterSummary(xs[0]);
    if (xs.length === 1) return head || "any";
    return `${head || "any"} (+${xs.length - 1} more)`;
  }

  // {min, max, range} -> short string. Aggregate is {kind: {amount|count: {value, window}}}.
  function aggregateSummary(agg) {
    if (!agg) return "";
    const [aggKind, aggBody] = variant(agg.kind);
    if (!aggKind) return "";
    const window = stripPrefix(aggBody.window).replace(/^/, "");
    if (aggKind === "count") {
      return `${aggBody.value} count (${window})`;
    }
    const amt = aggBody.value;
    if (amt && typeof amt === "object") {
      return `${amt.value} ${amt.currency} (${window})`;
    }
    return `${aggBody.value} (${window})`;
  }

  function boundSummary(bound) {
    if (!bound || !bound.kind) return "";
    const [side, body] = variant(bound.kind);
    if (side === "max") return `max ${aggregateSummary(body.aggregate)}`;
    if (side === "min") return `min ${aggregateSummary(body.aggregate)}`;
    if (side === "range") {
      const minS = aggregateSummary(body.min);
      const maxS = aggregateSummary(body.max);
      return `${minS} – ${maxS}`;
    }
    return "";
  }

  function allowSummary(allow) {
    if (!allow || isUnset(allow)) return "";
    return stripPrefix(allow);
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_policies();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        policies = res.body.policies ?? [];
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
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M8 1l6 2v5c0 3.5-2.5 6.5-6 7-3.5-.5-6-3.5-6-7V3l6-2zm-1 8.5L11 5.5l-1-1L7 7.5 5.5 6l-1 1L7 9.5z"/></svg>
      Policies
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
        <th>Policy ID</th>
        <th>Capabilities</th>
        <th>Limits</th>
        <th>Category</th>
        <th>Updated</th>
      </tr>
    </thead>
    <tbody>
      {#if policies.length === 0 && !loading}
        <tr><td colspan="6" class="empty">No policies</td></tr>
      {/if}
      {#each policies as policy}
        <tr class="policy-row" onclick={() => toggleExpanded(policy["policy-id"])}>
          <td>
            <span class="chevron">{expandedPolicy === policy["policy-id"] ? "▼" : "▶"}</span>
            <span class="policy-badge">{policy.name ?? "(unnamed)"}</span>
          </td>
          <td class="mono">{policy["policy-id"]}</td>
          <td>{policy.capabilities?.length ?? 0}</td>
          <td>{policy.limits?.length ?? 0}</td>
          <td class="capitalize">{stripPrefix(policy.category)}</td>
          <td title={policy["updated-at"]}>{time_ago(policy["updated-at"])}</td>
        </tr>
        {#if expandedPolicy === policy["policy-id"]}
          <tr class="detail-row">
            <td colspan="6">
              <div class="tab-bar">
                <button class="tab-btn" class:active={activeTab[policy["policy-id"]] === "capabilities"}
                        onclick={(e) => { e.stopPropagation(); switchTab(policy["policy-id"], "capabilities"); }}>
                  Capabilities
                </button>
                <button class="tab-btn" class:active={activeTab[policy["policy-id"]] === "limits"}
                        onclick={(e) => { e.stopPropagation(); switchTab(policy["policy-id"], "limits"); }}>
                  Limits
                </button>
              </div>

              {#if activeTab[policy["policy-id"]] === "capabilities"}
                {#if (policy.capabilities ?? []).length === 0}
                  <div class="detail-empty">No capabilities</div>
                {:else}
                  <table class="detail-table">
                    <thead>
                      <tr>
                        <th>Effect</th>
                        <th>Kind</th>
                        <th>Action</th>
                        <th>Filters</th>
                        <th>Reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each policy.capabilities as cap}
                        {@const [kindKey, kindBody] = variant(cap.kind)}
                        <tr>
                          <td>
                            <span class="effect-badge"
                                  class:allow={cap.effect === "effect-allow"}
                                  class:deny={cap.effect === "effect-deny"}>
                              {stripPrefix(cap.effect) || "—"}
                            </span>
                          </td>
                          <td class="capitalize">{(kindKey ?? "").replace(/-/g, " ")}</td>
                          <td>{stripPrefix(kindBody?.action) || "—"}</td>
                          <td class="muted">{filtersDisplay(kindBody?.filters)}</td>
                          <td class="muted">{cap.reason ?? ""}</td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                {/if}
              {:else if activeTab[policy["policy-id"]] === "limits"}
                {#if (policy.limits ?? []).length === 0}
                  <div class="detail-empty">No limits</div>
                {:else}
                  <table class="detail-table">
                    <thead>
                      <tr>
                        <th>Kind</th>
                        <th>Filters</th>
                        <th>Bound</th>
                        <th>Allow</th>
                        <th>Reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each policy.limits as limit}
                        {@const [kindKey, kindBody] = variant(limit.kind)}
                        <tr>
                          <td class="capitalize">{(kindKey ?? "").replace(/-/g, " ")}</td>
                          <td class="muted">{filtersDisplay(kindBody?.filters)}</td>
                          <td>{boundSummary(limit.bound)}</td>
                          <td class="muted">{allowSummary(limit.allow)}</td>
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

  .policy-row { cursor: pointer; }
  .policy-row:hover { background: var(--bg-hover); }

  .chevron {
    display: inline-block;
    width: 1.2em;
    font-size: 0.7rem;
    color: var(--text-muted);
  }

  .policy-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
    background: #dbeafe;
    color: #1e40af;
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

  .detail-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
  .detail-table th { background: transparent; font-size: 0.75rem; }
  .detail-table td { padding: 0.4rem 0.6rem; }

  .capitalize { text-transform: capitalize; }
  .muted { color: var(--text-muted); font-size: 0.85rem; }

  .effect-badge {
    display: inline-block;
    padding: 0.2rem 0.6rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    width: 60px;
    text-align: center;
  }

  .effect-badge.allow { background: #dcfce7; color: #166534; border-color: #86efac; }
  .effect-badge.deny { background: #fee2e2; color: #991b1b; border-color: #fca5a5; }
</style>
