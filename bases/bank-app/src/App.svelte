<script>
  import { init } from "./lib/api.mjs";
  import CreateParty from "./lib/CreateParty.svelte";
  import PartyList from "./lib/PartyList.svelte";
  import AccountList from "./lib/AccountList.svelte";
  import { onMount } from "svelte";

  let ready = $state(false);
  let error = $state(null);
  let partyListRef = $state();
  let accountListRef = $state();

  onMount(async () => {
    try {
      await init();
      ready = true;
    } catch (err) {
      error = err.message;
    }
  });
</script>

{#if error}
  <p>Failed to initialize: {error}</p>
{:else if ready}
  <main>
    <h1>Banking</h1>
    <CreateParty onCreated={() => partyListRef?.load()} />
    <PartyList bind:this={partyListRef}
               onAccountOpened={() => accountListRef?.load()} />
    <AccountList bind:this={accountListRef} />
  </main>
{:else}
  <p>Initializing...</p>
{/if}

<style>
  main {
    max-width: 720px;
    margin: 2rem auto;
    font-family: system-ui, -apple-system, sans-serif;
  }

  h1 {
    margin-bottom: 1.5rem;
  }
</style>
