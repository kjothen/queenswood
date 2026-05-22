<script>
  /* Dedicated sign-in chooser. Split-screen: brand on the left, the
     three identity options on the right. Each button hands a provider
     string (or null) to onSignIn, which auth.mjs translates into a
     kc.login({idpHint}) call — or, for null, Keycloak's own username/
     password form (used in dev with seeded `dev` / `dev`). */

  import { Logo, Wordmark, ThemeToggle } from "@queenswood/bank-ui";

  let { onSignIn } = $props();
</script>

<div class="page">
  <div class="theme-toggle-slot">
    <ThemeToggle />
  </div>

  <section class="brand">
    <div class="brand-inner">
      <div class="mark">
        <Logo variant="A" size={36} idPrefix="signin" />
        <span class="wm"><Wordmark variant="grotesk" size={16} /></span>
      </div>
      <h1>Sign in. <em>Spin up a bank.</em> Go.</h1>
      <div class="meta">
        <span>v. 2026.05 · console</span>
      </div>
    </div>
  </section>

  <section class="card">
    <div class="card-inner">
      <span class="eyebrow">Console · sign in</span>
      <h2>Welcome back.</h2>
      <p class="lede">
        Choose how you'd like to sign in to the Queenswood console.
      </p>

      <div class="options">
        <button type="button" class="opt" onclick={() => onSignIn("google")}>
          <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
          </svg>
          <span>Continue with Google</span>
        </button>

        <button type="button" class="opt" onclick={() => onSignIn("github")}>
          <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor" d="M12 .5C5.65.5.5 5.66.5 12.02c0 5.09 3.29 9.4 7.86 10.93.58.1.79-.25.79-.55 0-.27-.01-.99-.02-1.94-3.2.69-3.88-1.54-3.88-1.54-.52-1.33-1.27-1.69-1.27-1.69-1.04-.71.08-.69.08-.69 1.15.08 1.76 1.18 1.76 1.18 1.02 1.76 2.69 1.25 3.34.95.1-.75.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.18-3.08-.12-.29-.51-1.46.11-3.04 0 0 .97-.31 3.17 1.18a10.9 10.9 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.62 1.58.23 2.75.11 3.04.74.8 1.18 1.82 1.18 3.08 0 4.42-2.69 5.4-5.26 5.68.41.36.78 1.06.78 2.14 0 1.55-.01 2.8-.01 3.18 0 .3.21.66.8.55C20.21 21.42 23.5 17.11 23.5 12.02 23.5 5.66 18.35.5 12 .5z"/>
          </svg>
          <span>Continue with GitHub</span>
        </button>

        <div class="divider"><span>or</span></div>

        <button type="button" class="opt" onclick={() => onSignIn(null)}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="3" y="5" width="18" height="14" rx="2"/>
            <path d="M3 7l9 6 9-6"/>
          </svg>
          <span>Continue with email</span>
        </button>
      </div>

      <p class="foot">By continuing you agree to the Queenswood terms.</p>
    </div>
  </section>
</div>

<style>
  .page {
    display: grid;
    grid-template-columns: 1.05fr 1fr;
    min-height: 100vh;
    background: var(--surface);
    color: var(--fg);
    font-family: var(--grotesk);
    position: relative;
  }

  /* Top-right floating slot for the ThemeToggle. Avoids cluttering
     either the brand panel or the sign-in card — sign-in is the one
     page where a manual theme choice often happens first. */
  .theme-toggle-slot {
    position: absolute;
    top: 20px;
    right: 24px;
    z-index: 1;
  }

  .brand {
    background: var(--surface);
    border-right: 1px solid var(--rule);
    padding: 64px;
    display: flex;
    align-items: center;
  }
  .brand-inner {
    max-width: 440px;
  }
  .mark {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 56px;
  }
  .mark .wm {
    display: inline-block;
  }
  .brand h1 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 56px;
    line-height: 1.04;
    letter-spacing: -0.012em;
    margin: 0 0 36px;
    max-width: 14ch;
    text-wrap: pretty;
  }
  .brand h1 em {
    font-style: italic;
    color: var(--gold-deep);
    font-weight: 500;
  }
  .brand .meta {
    display: flex;
    align-items: center;
    gap: 12px;
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }

  .card {
    background: var(--surface);
    display: flex;
    align-items: center;
    padding: 64px;
  }
  .card-inner {
    width: 100%;
    max-width: 420px;
  }
  .eyebrow {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--fg-muted);
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  .eyebrow::before {
    content: "";
    width: 18px;
    height: 1px;
    background: var(--gold-deep);
  }
  .card h2 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 44px;
    line-height: 1.05;
    letter-spacing: -0.008em;
    margin: 14px 0 14px;
  }
  .lede {
    font-size: 15px;
    line-height: 1.55;
    color: var(--fg-2);
    margin: 0 0 28px;
    max-width: 38ch;
  }

  .options {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .opt {
    display: flex;
    align-items: center;
    gap: 12px;
    height: 48px;
    padding: 0 16px;
    border-radius: 8px;
    border: 1px solid var(--rule);
    background: transparent;
    color: var(--fg);
    font-family: var(--grotesk);
    font-size: 15px;
    font-weight: 500;
    cursor: pointer;
    text-align: left;
    transition:
      background 0.12s,
      border-color 0.12s,
      transform 0.08s;
  }
  .opt:hover {
    background: var(--hover-overlay);
    border-color: var(--rule);
  }
  .opt:active {
    transform: translateY(0.5px);
  }
  .opt svg {
    flex: 0 0 20px;
  }

  .divider {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 8px 0;
    color: var(--fg-muted);
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.18em;
    text-transform: uppercase;
  }
  .divider::before,
  .divider::after {
    content: "";
    flex: 1;
    height: 1px;
    background: var(--rule);
  }

  .foot {
    margin: 28px 0 0;
    font-size: 12px;
    color: var(--fg-muted);
    line-height: 1.55;
  }

  @media (max-width: 880px) {
    .page {
      grid-template-columns: 1fr;
    }
    .brand {
      border-right: 0;
      border-bottom: 1px solid var(--rule);
      padding: 48px 32px 32px;
    }
    .brand h1 {
      font-size: 40px;
      margin-bottom: 24px;
    }
    .mark {
      margin-bottom: 32px;
    }
    .card {
      padding: 32px;
    }
    .card h2 {
      font-size: 32px;
    }
  }
</style>
