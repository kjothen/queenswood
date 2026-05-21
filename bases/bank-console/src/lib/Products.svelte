<script>
  /* Products page — the first thing a signed-in console user lands on.
     Lists the org's cash-account-products, one row per version, and
     gates per-row actions on the version's status:

       draft     → Publish (brand), Edit (ghost), Discard (danger)
       published → New version (ghost)   — opens a fresh draft
       discarded → no actions (the row is a tombstone)

     Create / new-version / edit all push the user into ProductDrawer
     with the appropriate mode. The drawer owns the form; we own the
     list and refetch when the drawer reports a successful mutation. */

  import {
    PageHeader,
    Table,
    Thead,
    Tbody,
    Tr,
    Th,
    Td,
    Badge,
    Button,
  } from "@queenswood/bank-ui";
  import {
    list_cash_account_products,
    list_cash_account_product_templates,
    publish_cash_account_product,
    discard_cash_account_product_draft,
  } from "./api.mjs";
  import ProductDrawer from "./ProductDrawer.svelte";

  let { user, memberships } = $props();

  let loading = $state(true);
  let error = $state(null);
  let versions = $state([]);
  let templates = $state([]);

  // Drawer state. `mode` decides the form's behaviour; `target`
  // carries the version being edited when in 'edit-draft' mode.
  let drawerOpen = $state(false);
  let drawerMode = $state("create");
  let drawerTarget = $state(null);

  // The kicker is the org's name when /v1/me has surfaced it.
  // When absent we leave it undefined; PageHeader hides empty
  // kickers cleanly, which beats a stand-in "Console" placeholder.
  const kicker = $derived(memberships?.[0]?.["organization-name"]);

  // Backend statuses map straight to bank-ui badge tones except for
  // `discarded`, which is conceptually archived from the UI's POV.
  const TONE = {
    draft: "draft",
    published: "published",
    discarded: "archived",
  };

  function toneFor(status) {
    return TONE[status] ?? "neutral";
  }

  async function loadTemplates() {
    const res = await list_cash_account_product_templates();
    if (res.status >= 200 && res.status < 300) {
      templates = res.body?.items ?? [];
    }
    // Non-fatal: drawer falls back to a minimal template set.
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_cash_account_products();
      if (res.status >= 200 && res.status < 300) {
        // Response shape: {items: [{product-id, versions: [...]}]}.
        // Flatten to one row per version. For products that have no
        // open draft, mark the most recent published version as the
        // "new version" target so the row can offer that action.
        const items = res.body?.items ?? [];
        versions = items.flatMap((item) => {
          const vs = item.versions ?? [];
          const hasDraft = vs.some((v) => v.status === "draft");
          const reviseTarget = hasDraft
            ? null
            : vs.find((v) => v.status === "published");
          const reviseId = reviseTarget?.["version-id"] ?? null;
          return vs.map((v) => ({
            ...v,
            canRevise: v["version-id"] === reviseId,
          }));
        });
      } else {
        error = res.body?.detail ?? `HTTP ${res.status}`;
        versions = [];
      }
    } catch (err) {
      error = err.message;
      versions = [];
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    load();
    loadTemplates();
  });

  function openCreate() {
    drawerMode = "create";
    drawerTarget = null;
    drawerOpen = true;
  }

  function openEdit(version) {
    drawerMode = "edit-draft";
    drawerTarget = version;
    drawerOpen = true;
  }

  function openNewVersion(version) {
    drawerMode = "new-version";
    drawerTarget = version;
    drawerOpen = true;
  }

  async function publish(version) {
    const res = await publish_cash_account_product(
      version["product-id"],
      version["version-id"],
    );
    if (res.status >= 200 && res.status < 300) {
      load();
    } else {
      error = res.body?.detail ?? `Publish failed (${res.status})`;
    }
  }

  async function discard(version) {
    const res = await discard_cash_account_product_draft(
      version["product-id"],
      version["version-id"],
    );
    if (res.status >= 200 && res.status < 300) {
      load();
    } else {
      error = res.body?.detail ?? `Discard failed (${res.status})`;
    }
  }

  function formatRelative(iso) {
    if (!iso) return "—";
    const then = new Date(iso).getTime();
    const diff = (Date.now() - then) / 1000;
    if (diff < 60) return "just now";
    if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
    if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} d ago`;
    return new Date(iso).toLocaleDateString();
  }

  function currenciesLabel(v) {
    // The version surfaces a single `currency`; legacy responses
    // sometimes carry `allowed-currencies` as an array. Render
    // whichever is present.
    if (v.currency) return v.currency;
    if (Array.isArray(v["allowed-currencies"])) {
      return v["allowed-currencies"].join(", ");
    }
    return "—";
  }

</script>

<PageHeader {kicker} title="Products" sub="Drafts are iterable; publishing commits a version and auto-archives the one it supersedes.">
  {#snippet actions()}
    <Button variant="ghost" onclick={load}>Refresh</Button>
    <Button variant="primary" onclick={openCreate}>New product</Button>
  {/snippet}
</PageHeader>

{#if error}
  <div class="alert" role="alert">{error}</div>
{/if}

{#if loading}
  <div class="loading">Loading…</div>
{:else if versions.length === 0}
  <div class="empty">
    <p>No products yet.</p>
    <p class="hint">Click <strong>New product</strong> to create your first one.</p>
  </div>
{:else}
  <Table>
    <Thead>
      <Tr>
        <Th>ID</Th>
        <Th>Name</Th>
        <Th>Type</Th>
        <Th>Status</Th>
        <Th align="right">Rate (bps)</Th>
        <Th>Currency</Th>
        <Th>Created</Th>
        <Th align="right">Actions</Th>
      </Tr>
    </Thead>
    <Tbody>
      {#each versions as v (v["version-id"])}
        <Tr>
          <Td mono muted>{v["product-id"]}</Td>
          <Td emphasized>{v.name}</Td>
          <Td>{v["product-type"]}</Td>
          <Td><Badge tone={toneFor(v.status)}>{v.status}</Badge></Td>
          <Td align="right" mono tabular>{v["interest-rate-bps"] ?? 0}</Td>
          <Td>{currenciesLabel(v)}</Td>
          <Td muted>{formatRelative(v["created-at"])}</Td>
          <Td align="right">
            <span class="actions">
              {#if v.status === "draft"}
                <Button size="sm" variant="brand" onclick={() => publish(v)}>Publish</Button>
                <Button size="sm" variant="ghost" onclick={() => openEdit(v)}>Edit</Button>
                <Button size="sm" variant="danger" onclick={() => discard(v)}>Discard</Button>
              {:else if v.canRevise}
                <Button size="sm" variant="ghost" onclick={() => openNewVersion(v)}>New version</Button>
              {/if}
            </span>
          </Td>
        </Tr>
      {/each}
    </Tbody>
  </Table>
{/if}

<ProductDrawer
  open={drawerOpen}
  mode={drawerMode}
  target={drawerTarget}
  {templates}
  onClose={() => (drawerOpen = false)}
  onSaved={() => {
    drawerOpen = false;
    load();
  }}
/>

<style>
  .alert {
    padding: 12px 16px;
    border: 1px solid var(--rule);
    border-radius: 6px;
    background: var(--surface-sunk);
    color: var(--fg);
    font-size: 14px;
  }
  .loading,
  .empty {
    padding: 48px 16px;
    text-align: center;
    color: var(--fg-muted);
    border: 1px dashed var(--rule);
    border-radius: 12px;
  }
  .empty p {
    margin: 0;
  }
  .empty .hint {
    margin-top: 6px;
    font-size: 13px;
  }
  .empty strong {
    color: var(--fg);
    font-weight: 500;
  }
  .actions {
    display: inline-flex;
    gap: 6px;
    justify-content: flex-end;
    white-space: nowrap;
  }
</style>
