<script>
  let { user, memberships, onSignOut } = $props();
  const organization = $derived(memberships?.[0]);
</script>

<div class="dashboard">
  <header>
    <h1>Queenswood</h1>
    <div class="who">
      {#if user?.["avatar-url"]}
        <img src={user["avatar-url"]} alt="" class="avatar" />
      {/if}
      <span class="name">{user?.name}</span>
      <button onclick={onSignOut}>Sign out</button>
    </div>
  </header>
  <main>
    <section class="welcome">
      <h2>Welcome, {user?.name ?? "friend"}.</h2>
      {#if organization}
        <p>Organization: <strong>{organization["organization-id"]}</strong></p>
      {:else}
        <p>No organization yet.</p>
      {/if}
    </section>
  </main>
</div>

<style>
  .dashboard {
    min-height: 100vh;
    background: #f9fafb;
    color: #111827;
    font-family: system-ui, -apple-system, sans-serif;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1rem 2rem;
    background: #ffffff;
    border-bottom: 1px solid #e5e7eb;
  }

  h1 {
    margin: 0;
    font-size: 1.25rem;
  }

  .who {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }

  .avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
  }

  .name {
    color: #374151;
    font-size: 0.95rem;
  }

  header button {
    padding: 0.4rem 0.8rem;
    background: #ffffff;
    color: #111827;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    cursor: pointer;
    font: inherit;
  }

  main {
    padding: 2rem;
  }

  .welcome {
    max-width: 640px;
    padding: 2rem;
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
  }

  h2 {
    margin: 0 0 0.5rem;
    font-size: 1.5rem;
  }
</style>
