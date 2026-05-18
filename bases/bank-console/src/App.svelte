<script>
  import { ensure_session, sign_in, sign_out, token_claims } from "./lib/auth.mjs";
  import { get_me } from "./lib/api.mjs";
  import SignIn from "./lib/SignIn.svelte";
  import Onboarding from "./lib/Onboarding.svelte";
  import Dashboard from "./lib/Dashboard.svelte";

  // Three end states (sign-in / onboarding / dashboard) plus a
  // "loading" transient while Keycloak runs its silent SSO check
  // and we hit /v1/me. The state name drives which screen renders.
  let stage = $state("loading");
  let user = $state(null);
  let memberships = $state([]);

  // On every page-load: hand off to Keycloak to figure out whether
  // there's an existing session. If yes, GET /v1/me; the response
  // distinguishes "needs onboarding" (404) from "ready" (200).
  $effect(() => {
    bootstrap();
  });

  async function bootstrap() {
    const session = await ensure_session();
    if (!session.authenticated) {
      stage = "signin";
      return;
    }
    await refresh_me();
  }

  async function refresh_me() {
    const { status, body } = await get_me();
    if (status === 200) {
      user = body.user;
      memberships = body.memberships ?? [];
      stage = "dashboard";
    } else if (status === 404) {
      stage = "onboarding";
    } else {
      // Surface unexpected statuses (5xx, 401 after refresh) as a
      // sign-out — safer than a confusing dashboard with stale state.
      stage = "signin";
    }
  }

  function handleOnboardComplete(payload) {
    user = payload.user;
    memberships = [payload.membership];
    stage = "dashboard";
  }

  function defaultOrgName() {
    const claims = token_claims();
    return claims?.name ? `${claims.name}'s Organization` : "";
  }
</script>

{#if stage === "loading"}
  <div class="splash">Loading…</div>
{:else if stage === "signin"}
  <SignIn onSignIn={sign_in} />
{:else if stage === "onboarding"}
  <Onboarding
    defaultName={defaultOrgName()}
    onComplete={handleOnboardComplete}
    onSignOut={sign_out}
  />
{:else if stage === "dashboard"}
  <Dashboard {user} {memberships} onSignOut={sign_out} />
{/if}

<style>
  .splash {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    color: #6b7280;
    font-family: system-ui, -apple-system, sans-serif;
  }
</style>
