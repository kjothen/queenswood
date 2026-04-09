<script>
  import { create_organization, get_organization_policies, get_organization_limits, list_organizations, simulate_accrue, simulate_capitalize } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";
  import Modal from "./Modal.svelte";

  let { selectedOrgId, onSelectDefault, onCreated, onLoaded, showToast } = $props();

  let organizations = $state([]);
  let loading = $state(false);
  let error = $state(null);

  let modalOpen = $state(false);
  let orgName = $state("Galactic Bank");
  let currencies = $state("GBP");
  let creating = $state(false);
  let policies = $state([]);
  let limits = $state([]);
  let loadingDefaults = $state(false);

  function prettyLabel(s) {
    return String(s).replace(/^policy-capability-|^limit-type-/g, "").replace(/-/g, " ");
  }

  async function openCreateModal() {
    modalOpen = true;
    loadingDefaults = true;
    try {
      const [pRes, lRes] = await Promise.all([
        get_organization_policies(),
        get_organization_limits(),
      ]);
      if (pRes["http-status"] >= 200 && pRes["http-status"] < 300) {
        policies = structuredClone(pRes.body ?? []);
      }
      if (lRes["http-status"] >= 200 && lRes["http-status"] < 300) {
        limits = structuredClone(lRes.body ?? []);
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      loadingDefaults = false;
    }
  }

  function togglePolicyEffect(idx) {
    const cur = policies[idx].effect;
    policies[idx].effect = cur === "allow" ? "deny" : "allow";
  }

  function clamp(n, lower, upper) {
    let v = n;
    if (lower != null && v < lower) v = lower;
    if (upper != null && v > upper) v = upper;
    return v;
  }

  function formatNumber(n) {
    if (n == null || n === "") return "";
    return Number(n).toLocaleString("en-GB");
  }

  function onLimitInput(idx, e) {
    const raw = e.target.value.replace(/[^\d]/g, "");
    const parsed = raw === "" ? 0 : parseInt(raw, 10);
    const limit = limits[idx];
    const n = clamp(parsed, limit.lower, limit.upper);
    limits[idx].value = n;
    e.target.value = formatNumber(n);
  }
  let accruing = $state({});
  let capitalizing = $state({});
  let showDatePicker = $state(false);
  let datePickerAction = $state(null);
  let datePickerOrgId = $state(null);
  let datePickerDate = $state("");

  function todayISO() {
    return new Date().toISOString().slice(0, 10);
  }

  function dateToInt(isoDate) {
    const [y, m, d] = isoDate.split("-").map(Number);
    return y * 10000 + m * 100 + d;
  }

  function openDatePicker(orgId, action) {
    datePickerOrgId = orgId;
    datePickerAction = action;
    datePickerDate = todayISO();
    showDatePicker = true;
  }

  function closeDatePicker() {
    showDatePicker = false;
  }

  function todayAsInt() {
    const d = new Date();
    return d.getFullYear() * 10000
           + (d.getMonth() + 1) * 100
           + d.getDate();
  }

  function errorDetail(body) {
    if (!body) return null;
    return body.message ?? body.error ?? body.detail
           ?? (typeof body === "string" ? body : JSON.stringify(body));
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_organizations();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        organizations = res.body.organizations ?? [];
        onLoaded?.(organizations);
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      error = err.message;
    } finally {
      loading = false;
    }
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!orgName.trim()) return;
    creating = true;
    try {
      const currencyList = currencies.split(",").map(c => c.trim().toUpperCase()).filter(c => c);
      const res = await create_organization(orgName.trim(), currencyList, policies, limits);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        orgName = "";
        currencies = "GBP";
        modalOpen = false;
        showToast?.({ type: "success", message: "Organization created" });
        await load();
        onCreated?.(organizations);
      } else {
        showToast?.({ type: "warning", message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      creating = false;
    }
  }

  async function submitDatePicker() {
    const orgId = datePickerOrgId;
    const asOfDate = dateToInt(datePickerDate);
    const action = datePickerAction;
    showDatePicker = false;

    if (action === "accrue") {
      accruing[orgId] = true;
      try {
        const res = await simulate_accrue(orgId, asOfDate);
        if (res["http-status"] >= 200 && res["http-status"] < 300) {
          showToast?.({ type: "success",
                        message: `Accrued ${res.body["accounts-processed"]} accounts` });
        } else {
          showToast?.({ type: "warning",
                        message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
        }
      } catch (err) {
        showToast?.({ type: "error", message: err.message });
      } finally {
        delete accruing[orgId];
      }
    } else if (action === "capitalize") {
      capitalizing[orgId] = true;
      try {
        const res = await simulate_capitalize(orgId, asOfDate);
        if (res["http-status"] >= 200 && res["http-status"] < 300) {
          showToast?.({ type: "success",
                        message: `Capitalized ${res.body["accounts-processed"]} accounts` });
        } else {
          showToast?.({ type: "warning",
                        message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
        }
      } catch (err) {
        showToast?.({ type: "error", message: err.message });
      } finally {
        delete capitalizing[orgId];
      }
    }
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M3 1a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1H3zm1 2h2v2H4V3zm0 4h2v2H4V7zm0 4h2v2H4v-2zm4-8h2v2H8V3zm0 4h2v2H8V7zm0 4h2v2H8v-2z"/></svg>
      Organizations
    </h2>
    <div class="header-actions">
      <button class="new-btn" onclick={openCreateModal}>+ New Organization</button>
      <button class="refresh" onclick={() => load()} disabled={loading}>
        {loading ? "Loading..." : "Refresh"}
      </button>
    </div>
  </div>

  <Modal open={modalOpen} onClose={() => modalOpen = false} title="New Organization" maxWidth="640px">
    <form onsubmit={handleCreate}>
      <label>
        Organization Name
        <input
          type="text"
          bind:value={orgName}
          placeholder="Organization name"
          required
          disabled={creating}
        />
      </label>
      <label>
        Currencies
        <input
          type="text"
          bind:value={currencies}
          placeholder="GBP, USD"
          required
          disabled={creating}
        />
      </label>

      <hr class="form-divider" />

      <h4 class="form-section-title">Policies</h4>
      {#if loadingDefaults}
        <p class="form-loading">Loading defaults...</p>
      {:else if policies.length === 0}
        <p class="form-loading">No policies</p>
      {:else}
        <ul class="rules-list">
          {#each policies as policy, idx}
            <li class="rule-row">
              <span class="rule-label">{prettyLabel(policy.capability)}</span>
              <button
                type="button"
                class="effect-toggle"
                class:allow={policy.effect === "allow"}
                class:deny={policy.effect === "deny"}
                onclick={() => togglePolicyEffect(idx)}
                disabled={creating}
              >
                {policy.effect}
              </button>
            </li>
          {/each}
        </ul>
      {/if}

      <hr class="form-divider" />

      <h4 class="form-section-title">Limits</h4>
      {#if loadingDefaults}
        <p class="form-loading">Loading defaults...</p>
      {:else if limits.length === 0}
        <p class="form-loading">No limits</p>
      {:else}
        <ul class="rules-list">
          {#each limits as limit, idx}
            <li class="rule-row">
              <span class="rule-label">{prettyLabel(limit.type)}</span>
              <input
                type="text"
                inputmode="numeric"
                class="limit-value"
                value={formatNumber(limit.value)}
                oninput={(e) => onLimitInput(idx, e)}
                disabled={creating}
              />
            </li>
          {/each}
        </ul>
      {/if}

      <button type="submit" disabled={creating || !orgName.trim() || loadingDefaults}>
        {creating ? "Creating..." : "Create Organization"}
      </button>
    </form>
  </Modal>

  {#if showDatePicker}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="dialog-overlay" onclick={closeDatePicker}>
      <div class="dialog" onclick={(e) => e.stopPropagation()}>
        <h3>{datePickerAction === "accrue" ? "Accrue Interest" : "Capitalize Interest"}</h3>
        <label>
          As-of Date
          <input type="date" bind:value={datePickerDate} min={todayISO()} />
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closeDatePicker}>Cancel</button>
          <button class="submit-btn" onclick={submitDatePicker} disabled={!datePickerDate}>
            {datePickerAction === "accrue" ? "Accrue" : "Capitalize"}
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Name</th>
        <th>Type</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if organizations.length === 0 && !loading}
        <tr><td colspan="7" class="empty">No organizations</td></tr>
      {/if}
      {#each organizations as org}
        <tr>
          <td class="mono">{org["organization-id"]}</td>
          <td>{org.name}</td>
          <td>
            <span class="type-badge" class:internal={org.type === "internal"}>
              {org.type}
            </span>
          </td>
          <td>
            <span class="status-badge"
                  class:active={org.status === "active"}>
              {org.status}
            </span>
          </td>
          <td title={org["created-at"]}>{time_ago(org["created-at"])}</td>
          <td title={org["updated-at"]}>{time_ago(org["updated-at"])}</td>
          <td>
            {#if org.type !== "internal"}
              <button
                class="interest-btn"
                disabled={accruing[org["organization-id"]]}
                onclick={() => openDatePicker(org["organization-id"], "accrue")}
              >
                {accruing[org["organization-id"]] ? "..." : "Accrue"}
              </button>
              <button
                class="interest-btn"
                disabled={capitalizing[org["organization-id"]]}
                onclick={() => openDatePicker(org["organization-id"], "capitalize")}
              >
                {capitalizing[org["organization-id"]] ? "..." : "Capitalize"}
              </button>
            {/if}
            {#if org["organization-id"] === selectedOrgId}
              <span class="default-badge">Default</span>
            {:else if org.type !== "internal"}
              <button
                class="action-btn"
                onclick={() => onSelectDefault(org["organization-id"])}
              >
                Set Default
              </button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</section>

<style>
  section {
    margin-top: 0;
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }

  .header-actions {
    display: flex;
    gap: 0.5rem;
  }

  h2 {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .new-btn {
    padding: 0.4rem 0.8rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .new-btn:hover {
    background: #15803d;
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

  form {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  form label {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-weight: 500;
  }

  form input {
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
  }

  form button {
    padding: 0.5rem 1rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.9rem;
    cursor: pointer;
  }

  form button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .form-divider {
    border: none;
    border-top: 1px solid var(--border);
    margin: 0.5rem 0;
  }

  .form-section-title {
    margin: 0 0 0.25rem;
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--text-muted);
  }

  .tab-bar {
    display: flex;
    gap: 0.25rem;
    margin-bottom: 0.5rem;
  }

  .tab-btn {
    padding: 0.3rem 0.8rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    background: var(--bg);
    color: var(--text-muted);
    font-size: 0.8rem;
    cursor: pointer;
    text-transform: capitalize;
  }

  .tab-btn.active {
    background: var(--bg-secondary);
    color: var(--text);
    font-weight: 600;
  }

  .tab-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .form-loading {
    color: var(--text-faint);
    font-size: 0.85rem;
    margin: 0;
  }

  .rules-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .rule-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem;
  }

  .rule-checkbox {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 0.5rem;
    flex: 1;
    font-weight: 400;
    font-size: 0.85rem;
    text-transform: capitalize;
  }

  .rule-checkbox input[type="checkbox"] {
    width: auto;
    margin: 0;
  }

  .rule-label {
    flex: 1;
  }

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

  .effect-toggle.allow {
    background: #dcfce7;
    color: #166534;
    border-color: #86efac;
  }

  .effect-toggle.deny {
    background: #fee2e2;
    color: #991b1b;
    border-color: #fca5a5;
  }

  .effect-toggle:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .limit-value {
    width: 110px !important;
    padding: 0.3rem 0.5rem !important;
    font-size: 0.85rem !important;
    text-align: right;
  }

  .limit-value:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .limit-input-group {
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  .limit-currency {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--text-muted);
    text-transform: uppercase;
  }

  .rule-checkbox:has(input:not(:checked)) ~ .limit-input-group .limit-currency {
    opacity: 0.5;
  }

  .rule-checkbox:has(input:not(:checked)) .rule-label {
    opacity: 0.5;
  }

  .error-msg {
    background: var(--bg-error);
    border: 1px solid var(--border-error);
    padding: 0.75rem;
    border-radius: 4px;
    margin-bottom: 1rem;
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

  .empty {
    text-align: center;
    color: var(--text-faint);
    padding: 1.5rem;
  }

  .type-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
    background: #e0e7ff;
    color: #3730a3;
  }

  .type-badge.internal {
    background: #fef3c7;
    color: #92400e;
  }

  .status-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
  }

  .status-badge.active {
    background: #dcfce7;
    color: #166534;
  }

  .default-badge {
    display: inline-block;
    padding: 0.2rem 0.5rem;
    background: #dbeafe;
    color: #1d4ed8;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
  }

  .dialog-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 100;
  }

  .dialog {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.5rem;
    width: 320px;
    max-width: 90vw;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  }

  .dialog h3 {
    margin: 0 0 1rem;
  }

  .dialog label {
    display: block;
    margin-bottom: 0.75rem;
    font-size: 0.85rem;
    font-weight: 600;
  }

  .dialog input {
    display: block;
    width: 100%;
    margin-top: 0.25rem;
    padding: 0.4rem 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
    box-sizing: border-box;
  }

  .dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 0.5rem;
    margin-top: 1rem;
  }

  .cancel-btn {
    padding: 0.4rem 0.8rem;
    background: var(--bg-secondary);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
    color: var(--text);
  }

  .submit-btn {
    padding: 0.4rem 0.8rem;
    background: #7c3aed;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .submit-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .submit-btn:not(:disabled):hover {
    background: #6d28d9;
  }

  .interest-btn {
    padding: 0.25rem 0.6rem;
    background: #7c3aed;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
    margin-right: 0.25rem;
  }

  .interest-btn:hover:not(:disabled) {
    background: #6d28d9;
  }

  .interest-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .action-btn {
    padding: 0.25rem 0.6rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .action-btn:hover {
    background: #1d4ed8;
  }
</style>
