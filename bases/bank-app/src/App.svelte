<script>
  import { ensure_session, sign_in, sign_out } from "./lib/auth.mjs";
  import { set_org, clear_org_credentials } from "./lib/api.mjs";
  import SignIn from "./lib/SignIn.svelte";
  import Sidebar from "./lib/Sidebar.svelte";
  import OrgSelector from "./lib/OrgSelector.svelte";
  import OrganizationList from "./lib/OrganizationList.svelte";
  import CreateParty from "./lib/CreateParty.svelte";
  import PartyList from "./lib/PartyList.svelte";
  import CashAccountList from "./lib/CashAccountList.svelte";
  import CashAccountProductList from "./lib/CashAccountProductList.svelte";
  import TierList from "./lib/TierList.svelte";
  import PolicyList from "./lib/PolicyList.svelte";
  import PayeeCheckList from "./lib/PayeeCheckList.svelte";
  import Toast from "./lib/Toast.svelte";

  // Two transient states (loading / signin) and one steady state
  // (dashboard) — bank-app doesn't have an onboarding screen because
  // ops users come pre-provisioned (the auth interceptor upserts
  // their User row on first sign-in, no org-creation step).
  let stage = $state("loading");

  let currentPage = $state("organizations");
  let organizations = $state([]);
  let selectedOrgId = $state(null);
  let partyListRef = $state();
  let accountListRef = $state();
  let productListRef = $state();
  let copListRef = $state();
  let toastRef = $state();

  let hasApiKey = $derived(selectedOrgId != null);

  $effect(() => {
    bootstrap();
  });

  async function bootstrap() {
    const session = await ensure_session();
    stage = session.authenticated ? "dashboard" : "signin";
  }

  function handleLogout() {
    clear_org_credentials();
    selectedOrgId = null;
    organizations = [];
    stage = "signin";
    sign_out();
  }

  function showToast(opts) {
    toastRef?.show(opts);
  }

  async function selectOrg(orgId) {
    selectedOrgId = orgId;
    // set_org mints a per-org JWT via /oauth/token when the org's
    // client_credentials are stored locally (orgs created in this
    // session). For orgs we don't have credentials for, set_org
    // falls back to the ops user JWT (admin role).
    await set_org(orgId);
    partyListRef?.load();
    accountListRef?.load();
    productListRef?.load();
    copListRef?.load();
  }

  function firstCustomerOrg(orgs) {
    return orgs.find(o => o.type !== "internal");
  }

  function handleOrgCreated(orgs) {
    organizations = orgs;
    if (!selectedOrgId) {
      const org = firstCustomerOrg(orgs);
      if (org) selectOrg(org["organization-id"]);
    }
  }

  function handleOrgsLoaded(orgs) {
    organizations = orgs;
    if (!selectedOrgId) {
      const org = firstCustomerOrg(orgs);
      if (org) selectOrg(org["organization-id"]);
    }
  }
</script>

<Toast bind:this={toastRef} />

{#if stage === "loading"}
  <div class="splash">Loading…</div>
{:else if stage === "signin"}
  <SignIn onSignIn={sign_in} />
{:else}
<div class="layout">
  <Sidebar {currentPage} onNavigate={(page) => currentPage = page} onLogout={handleLogout} />
  <main>
    {#if currentPage === "organizations"}
      <OrganizationList
        {selectedOrgId}
        onSelectDefault={(id) => selectOrg(id)}
        onCreated={handleOrgCreated}
        onLoaded={handleOrgsLoaded}
        {showToast}
      />
    {:else if currentPage === "tiers"}
      <TierList {showToast} />
    {:else if currentPage === "policies"}
      <PolicyList {showToast} />
    {:else if !hasApiKey}
      <div class="no-org">
        <p>Create an organization first.</p>
        <button onclick={() => currentPage = "organizations"}>
          Go to Organizations
        </button>
      </div>
    {:else if currentPage === "parties"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <PartyList bind:this={partyListRef}
                 onAccountOpened={() => accountListRef?.load()}
                 {showToast}>
        {#snippet headerActions()}
          <CreateParty onCreated={() => partyListRef?.load()} {showToast} />
        {/snippet}
      </PartyList>
    {:else if currentPage === "accounts"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <CashAccountList bind:this={accountListRef} orgId={selectedOrgId} {showToast} />
    {:else if currentPage === "products"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <CashAccountProductList bind:this={productListRef} {showToast} />
    {:else if currentPage === "cop"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <PayeeCheckList bind:this={copListRef} />
    {/if}
  </main>
</div>
{/if}

<style>
  :global(:root) {
    --bg: #ffffff;
    --bg-secondary: #f9fafb;
    --bg-hover: #e5e7eb;
    --bg-input: #ffffff;
    --bg-dropdown: #ffffff;
    --bg-error: #fee2e2;
    --bg-pagination: #f3f4f6;
    --text: #111827;
    --text-secondary: #374151;
    --text-muted: #6b7280;
    --text-faint: #9ca3af;
    --border: #e5e7eb;
    --border-input: #d1d5db;
    --border-error: #fca5a5;
    --details-border: #e5e7eb;
    color-scheme: light;
  }

  :global(:root.dark) {
    --bg: #0f172a;
    --bg-secondary: #1e293b;
    --bg-hover: #334155;
    --bg-input: #1e293b;
    --bg-dropdown: #1e293b;
    --bg-error: #450a0a;
    --bg-pagination: #1e293b;
    --text: #e2e8f0;
    --text-secondary: #cbd5e1;
    --text-muted: #94a3b8;
    --text-faint: #64748b;
    --border: #334155;
    --border-input: #475569;
    --border-error: #7f1d1d;
    --details-border: #334155;
    color-scheme: dark;
  }

  :global(body) {
    background: var(--bg);
    color: var(--text);
  }

  .layout {
    display: flex;
    height: 100vh;
    font-family: system-ui, -apple-system, sans-serif;
  }

  main {
    flex: 1;
    padding: 2rem;
    overflow-y: auto;
    max-width: 1400px;
    background: var(--bg);
    color: var(--text);
  }

  .no-org {
    padding: 2rem;
    text-align: center;
    color: var(--text-muted);
  }

  .no-org button {
    margin-top: 1rem;
    padding: 0.5rem 1rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
  }

  .splash {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    color: var(--text-muted, #6b7280);
    font-family: system-ui, -apple-system, sans-serif;
  }
</style>
