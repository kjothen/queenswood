<script>
  /* Ledger Accounts page — the bank's chart of accounts (GL accounts),
     rendered as a tree-table: each account row decomposes into the
     balances that comprise it. Read-only by design; creation of GL
     accounts happens when a bank is provisioned, not from here.

     The list endpoint returns accounts without balances, so we fetch
     the list and then each account's balances (a small, fixed chart —
     a handful of accounts — so the N+1 is fine). A balance is keyed by
     (balance-type, balance-status) and carries credit/debit in minor
     units; its signed net is credit − debit (credit-positive), which is
     what the backend's own available-balance derivation uses. The
     account's available figure is the sum of its balances' nets — the
     tree is a real decomposition, so we derive the total via sumMinor
     rather than trusting a separate field. */

  import {
    PageHeader,
    Button,
    Table,
    Thead,
    Tbody,
    Tr,
    Th,
    Td,
    Expander,
    MoneyCell,
    Phase,
    sumMinor,
  } from "@queenswood/bank-ui";
  import {
    list_ledger_accounts,
    list_ledger_account_balances,
  } from "./api.mjs";

  let { user, memberships } = $props();

  let loading = $state(true);
  let error = $state(null);
  let accounts = $state([]);
  // Open-state map keyed by account id. Accounts start collapsed; the
  // balance count sits in its own column, and Expand all reveals the
  // decomposition on demand.
  let open = $state({});

  const kicker = $derived(memberships?.[0]?.["bank-name"]);

  const allExpanded = $derived(
    accounts.length > 0 && accounts.every((a) => open[a.id]),
  );

  // A balance's signed net (credit-positive minor units) — matches the
  // backend's credit − debit convention, so liabilities read positive
  // and asset/overdraft positions read negative (→ danger tone).
  function netMinor(b) {
    return (b.credit ?? 0) - (b.debit ?? 0);
  }

  function mapBalance(b) {
    return {
      type: b["balance-type"],
      phase: b["balance-status"],
      currency: b.currency,
      minor: netMinor(b),
    };
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_ledger_accounts();
      if (res.status < 200 || res.status >= 300) {
        error = res.body?.detail ?? `HTTP ${res.status}`;
        accounts = [];
        return;
      }
      const list = res.body?.["ledger-accounts"] ?? [];
      // Fetch every account's balances in parallel, then shape each
      // account for the tree-table.
      const enriched = await Promise.all(
        list.map(async (a) => {
          const id = a["account-id"];
          const bres = await list_ledger_account_balances(id);
          const balances =
            bres.status >= 200 && bres.status < 300
              ? (bres.body?.balances ?? []).map(mapBalance)
              : [];
          return {
            id,
            name: a.name,
            gl: a["gl-code"],
            ccy: a.currency,
            balances,
          };
        }),
      );
      accounts = enriched;
      open = Object.fromEntries(enriched.map((a) => [a.id, false]));
    } catch (err) {
      error = err.message;
      accounts = [];
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    load();
  });

  function toggle(id) {
    open[id] = !open[id];
  }

  function onKey(e, id) {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      toggle(id);
    }
  }

  function toggleAll() {
    const next = !allExpanded;
    open = Object.fromEntries(accounts.map((a) => [a.id, next]));
  }
</script>

<PageHeader
  {kicker}
  title="Ledger Accounts"
  sub="The bank's chart of accounts. Each account decomposes into the balances that comprise it; the available figure is their sum."
>
  {#snippet actions()}
    <Button variant="ghost" onclick={load}>Refresh</Button>
    {#if accounts.length > 0}
      <Button variant="line" onclick={toggleAll}>
        {allExpanded ? "Collapse all" : "Expand all"}
      </Button>
    {/if}
  {/snippet}
</PageHeader>

{#if error}
  <div class="alert" role="alert">{error}</div>
{/if}

{#if loading}
  <div class="loading">Loading…</div>
{:else if accounts.length === 0}
  <div class="empty">
    <p>No ledger accounts.</p>
    <p class="hint">A bank's chart of accounts is seeded when the bank is provisioned.</p>
  </div>
{:else}
  <Table tree>
    <Thead>
      <Tr>
        <Th />
        <Th>ID</Th>
        <Th>Name</Th>
        <Th>GL Code</Th>
        <Th align="right">Balances</Th>
        <Th align="right">Available Balance</Th>
      </Tr>
    </Thead>
    <Tbody>
      {#each accounts as acc (acc.id)}
        <Tr
          expandable
          expanded={open[acc.id]}
          onclick={() => toggle(acc.id)}
          onkeydown={(e) => onKey(e, acc.id)}
        >
          <Td expander><Expander /></Td>
          <Td mono muted>{acc.id}</Td>
          <Td emphasized>{acc.name}<span class="qw-denom">{acc.ccy}</span></Td>
          <Td mono>{acc.gl}</Td>
          <Td align="right" mono muted tabular>{acc.balances.length}</Td>
          <MoneyCell minor={sumMinor(acc.balances)} ccy={acc.ccy} emphasized />
        </Tr>
        {#if open[acc.id]}
          {#each acc.balances as b, i (b.type + ":" + b.phase)}
            <Tr balance last={i === acc.balances.length - 1}>
              <Td expander />
              <Td />
              <Td addr>
                <span class="qw-tree-mark">
                  <span class="qw-addr-path">{b.type}</span>
                  <Phase phase={b.phase} />
                </span>
              </Td>
              <Td mono muted>{b.currency}</Td>
              <Td />
              <MoneyCell minor={b.minor} ccy={acc.ccy} />
            </Tr>
          {/each}
        {/if}
      {/each}
    </Tbody>
  </Table>
{/if}

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
</style>
