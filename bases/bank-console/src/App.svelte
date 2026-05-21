<script>
  import Router from "svelte-spa-router";
  import { wrap } from "svelte-spa-router/wrap";
  import { ensure_session, sign_in, sign_out, token_claims } from "./lib/auth.mjs";
  import { get_me } from "./lib/api.mjs";
  import Landing from "./lib/Landing.svelte";
  import SignInPage from "./lib/SignInPage.svelte";
  import Onboarding from "./lib/Onboarding.svelte";
  import Dashboard from "./lib/Dashboard.svelte";

  // Unauthenticated surfaces are URL-routed so /#/sign-in is shareable
  // and the marketing landing has a stable home. Authenticated screens
  // (onboarding / dashboard) stay state-driven — they have no need for
  // URLs of their own and that keeps the post-login redirect trivial.
  const unauthRoutes = {
    "/": Landing,
    "/sign-in": wrap({ component: SignInPage, props: { onSignIn: sign_in } }),
    "*": Landing,
  };

  // Three end states (sign-in / onboarding / dashboard) plus a
  // "loading" transient while Keycloak runs its silent SSO check
  // and we hit /v1/me. The state name drives which screen renders.
  let stage = $state("loading");
  let user = $state(null);
  let memberships = $state([]);

  // On every page-load: hand off to Keycloak to figure out whether
  // there's an existing session. If yes, GET /v1/me. bank-api's auth
  // interceptor upserts the User row on every authenticated request,
  // so /v1/me always returns 200 — we route by whether the response
  // carries any memberships (none means: needs onboarding).
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
    if (status !== 200) {
      // Unexpected status (5xx, 401 after refresh) — safer to send
      // the user back to sign-in than to render stale state.
      stage = "signin";
      return;
    }
    user = body.user;
    memberships = body.memberships ?? [];
    stage = memberships.length === 0 ? "onboarding" : "dashboard";
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
  <Router routes={unauthRoutes} />
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
