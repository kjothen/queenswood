<script>
  import { init } from "./lib/api.mjs";
  import CreateAccount from "./lib/CreateAccount.svelte";
  import AccountList from "./lib/AccountList.svelte";
  import { onMount } from "svelte";

  let ready = $state(false);
  let error = $state(null);

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
    <h1>Accounts</h1>
    <CreateAccount />
    <AccountList />
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
