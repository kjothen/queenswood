<script>
  import CreateAccount from "./lib/CreateAccount.svelte";
</script>

<main>
  <h1>Accounts</h1>
  <CreateAccount />
</main>

<style>
  main {
    max-width: 480px;
    margin: 2rem auto;
    font-family: system-ui, -apple-system, sans-serif;
  }

  h1 {
    margin-bottom: 1.5rem;
  }
</style>
