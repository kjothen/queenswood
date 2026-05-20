<script>
  /* Marketing-style landing for the unauthenticated bank-console.
     Structure ported from design_handoff_queenswood_logo/Queenswood Home.html.
     The two "Sign in with Google" CTAs (nav + final) call the existing
     `onSignIn` prop, which hands off to Keycloak via `auth.mjs/sign_in`. */

  import Logo from "./Logo.svelte";
  import Wordmark from "./Wordmark.svelte";

  let { onSignIn } = $props();
</script>

<div class="announce">
  <span class="pill">v0.1.0</span>
  <span>Open source · MIT licensed</span>
  <a href="https://github.com/repldriven/queenswood">GitHub ↗</a>
</div>

<nav class="nav">
  <div class="nav-inner">
    <a class="brand" href="/">
      <Logo variant="A" size={28} idPrefix="nav" />
      <span class="wm"><Wordmark variant="grotesk" size={14} /></span>
    </a>
    <ul>
      <li><a href="#platform">Platform</a></li>
      <li><a href="#engineering">Engineering</a></li>
      <li><a href="https://github.com/repldriven/queenswood">GitHub</a></li>
    </ul>
    <div class="spacer"></div>
    <div class="cta-row">
      <button class="btn solid" onclick={onSignIn}>Sign in with Google →</button>
    </div>
  </div>
</nav>

<section class="hero">
  <div class="wrap">
    <span class="ornament" aria-hidden="true">
      <Logo variant="A" size={460} idPrefix="hero-orn" />
    </span>
    <div class="grid">
      <div>
        <span class="eyebrow">Banking platform · v0.1.0</span>
        <h1 class="title">A multi-tenant<br /><em>banking platform.</em></h1>
        <p class="lede">
          Core banking with double-entry transactions and interest
          accrual, UK Faster Payments, and tenant onboarding with IDV.
          One unified API. Open source.
        </p>
        <div class="ctas">
          <button class="btn solid" onclick={onSignIn}>Sign in with Google →</button>
          <a class="btn line" href="https://github.com/repldriven/queenswood#readme">Read the docs</a>
          <a class="btn ghost" href="https://github.com/repldriven/queenswood">View on GitHub ↗</a>
        </div>
        <div class="meta">
          <span class="dot"></span>
          <span>UK Faster Payments</span>
          <span>·</span>
          <span>Onfido&nbsp;IDV</span>
          <span>·</span>
          <span>OAuth&nbsp;via&nbsp;Keycloak</span>
          <span>·</span>
          <span>OpenAPI&nbsp;3.x</span>
        </div>
      </div>
      <div>
        <div class="code" aria-label="API example">
          <div class="bar">
            <div class="dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
            <span>~/queenswood · zsh</span>
          </div>
<pre class="body"><span class="c"># Authed via OAuth (Keycloak). Issue an API key for machine-to-machine.</span>
<span class="k">curl</span> -X POST https://api.queenswood.local/v1/organisations \
  -H <span class="s">"Authorization: Bearer $QW_OAUTH_TOKEN"</span> \
  -H <span class="s">"Content-Type: application/json"</span> \
  -d <span class="s">{`'{ "name": "northwind-fs",
       "jurisdiction": "GB" }'`}</span>

<span class="c">#</span> <span class="g">{`{ "id": "org_01HW7…",`}</span>
<span class="c">#  </span>  <span class="g">{`"status": "active" }`}</span></pre>
        </div>
      </div>
    </div>
  </div>
</section>

<section id="platform" class="band">
  <div class="wrap">
    <div class="row">
      <h2>One unified API. <span class="num">The spec is the contract</span> — bank-shaped, not implementation-shaped.</h2>
      <div class="arr"><a href="https://github.com/repldriven/queenswood/blob/main/docs/adr/0013-single-unified-api.md">Read ADR-0013 →</a></div>
    </div>
  </div>
</section>

<section class="feat">
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="num">01 — Onboarding</span>
        <h3>Sign in. <em>Spin up a tenant.</em> Go.</h3>
        <p>
          Humans sign in through Keycloak — OAuth, SSO, social login,
          whatever your IdP supports. Tenants land in the organisation
          console, charter an organisation, and issue API keys for the
          services that need them. Operators get a separate console
          for platform-wide policy and review.
        </p>
        <ul>
          <li>Two consoles: <code>bank-app</code> (operator) and <code>bank-console</code> (organisation)</li>
          <li>OAuth via bundled Keycloak — swap in your own IdP at deploy</li>
          <li>API keys for machine-to-machine — returned once, stored hashed</li>
          <li>Capabilities &amp; limits as policy data; curative-permit self-correction</li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
          console / organisations
        </div>
        <div class="inner">
          <table class="org-table">
            <thead><tr><th>Tenant</th><th>Created</th><th>Cash accts</th><th>Status</th></tr></thead>
            <tbody>
              <tr><td>tenant_01HW7Z…</td><td>just now</td><td>0</td><td><span class="status">Active</span></td></tr>
              <tr><td>tenant_01HW5K…</td><td>3 days ago</td><td>1,284</td><td><span class="status">Active</span></td></tr>
              <tr><td>tenant_01HVTM…</td><td>1 week ago</td><td>602</td><td><span class="status">Active</span></td></tr>
              <tr><td>tenant_01HVQ4…</td><td>2 weeks ago</td><td>14</td><td><span class="status pending">Onboarding</span></td></tr>
              <tr><td>tenant_01HV9P…</td><td>1 month ago</td><td>3,901</td><td><span class="status">Active</span></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="feat reverse">
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="num">02 — Payments</span>
        <h3>UK Faster Payments. <em>Double-entry,</em> all the way down.</h3>
        <p>
          Internal transfers and outbound FPS via a pluggable scheme
          adapter. Inbound settlement with BBAN lookup and idempotency.
          Every transfer is a balanced pair of postings against typed
          accounts — every step a typed event your team can replay.
        </p>
        <ul>
          <li>SCAN payment addresses — sort code &amp; account number</li>
          <li>ClearBank adapter for FPS, with a simulator for dev &amp; tests</li>
          <li>Anomalies, not exceptions: <code>error</code>, <code>rejection</code>, <code>unauthorized</code></li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
          payment / pmt_01HW8Z…
        </div>
        <div class="inner">
          <div class="trail">
            <div class="step done">
              <span class="t">09:41:02</span>
              <span class="pin"></span>
              <span class="desc">payment-submitted<span class="ref">12,400.00 GBP → 04-00-04 / 12345678</span></span>
              <span class="kind">api</span>
            </div>
            <div class="step done">
              <span class="t">09:41:02</span>
              <span class="pin"></span>
              <span class="desc">policy-evaluated<span class="ref">limits ok · curative-permit not required</span></span>
              <span class="kind">policy</span>
            </div>
            <div class="step done">
              <span class="t">09:41:03</span>
              <span class="pin"></span>
              <span class="desc">submit-payment<span class="ref">scheme channel · ClearBank</span></span>
              <span class="kind">pulsar</span>
            </div>
            <div class="step active">
              <span class="t">09:41:04</span>
              <span class="pin"></span>
              <span class="desc">transaction-settled<span class="ref">FPS webhook · two postings written</span></span>
              <span class="kind">fdb</span>
            </div>
            <div class="step">
              <span class="t">—</span>
              <span class="pin"></span>
              <span class="desc">payment-completed</span>
              <span class="kind">api</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="feat last">
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="num">03 — Interest</span>
        <h3>Daily accrual that <em>conserves pennies.</em></h3>
        <p>
          Integer micro-unit arithmetic with sub-minor-unit carry —
          so fractional interest accrues precisely every day, and
          monthly capitalisation posts a six-leg entry that ties out
          to the penny. Cadence (daily, monthly, anything) is the
          operator's choice.
        </p>
        <ul>
          <li>Micro-unit precision · no floating point anywhere</li>
          <li>Six-leg postings at capitalisation</li>
          <li>Configurable per cash-account product, versioned at publish</li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
          interest / acct_treasury_01
        </div>
        <div class="inner">
          <div class="accrual">
            <div class="day"><span class="lab">DAY</span><span class="lab">Balance · μGBP</span><span class="lab">Accrued · μGBP</span><span class="lab">Carry</span></div>
            <div class="day"><span class="micro">01 May</span><span class="micro">412,310.550000</span><span class="micro">282.4521</span><span class="carry">+0.4521</span></div>
            <div class="day"><span class="micro">02 May</span><span class="micro">399,910.550000</span><span class="micro">273.9111</span><span class="carry">+0.3632</span></div>
            <div class="day"><span class="micro">03 May</span><span class="micro">404,980.950000</span><span class="micro">277.3850</span><span class="carry">+0.7482</span></div>
            <div class="day"><span class="micro">…</span><span class="micro">average · 405k</span><span class="micro">8,420.6611</span><span class="carry">+0.0123</span></div>
            <div class="day cap">
              <span class="micro cap-day">31 May · CAP</span>
              <span class="micro">capitalised</span>
              <span class="post">+8,420.66 GBP</span>
              <span class="carry">·6 legs</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section id="engineering" class="principles">
  <div class="wrap">
    <span class="eyebrow">Engineering choices</span>
    <h2>The interesting bits — <em>for engineers</em> who'd actually read the ADRs.</h2>
    <p class="lead">
      Queenswood is opinionated. Eight choices that show up everywhere in
      the codebase, each documented in a single decision record you can
      read on a coffee break.
    </p>
    <div class="grid">
      <div class="pri">
        <span class="num">ADR-0013 · 0014</span>
        <h4>One unified API. OpenAPI is the contract.</h4>
        <p>Bank-shaped, not implementation-shaped. The spec drives client generation, validation, and documentation — there is no second source of truth.</p>
      </div>
      <div class="pri">
        <span class="num">policy-evaluation</span>
        <h4>Policies as data, not hard-coded rules.</h4>
        <p>Capabilities and limits are records. A curative-permit pattern lets a tenant self-correct out of breach without a manual override.</p>
      </div>
      <div class="pri">
        <span class="num">interest</span>
        <h4>Pennies are conserved by construction.</h4>
        <p>Integer micro-unit arithmetic with sub-minor-unit carry. Daily accrual, monthly capitalisation, six-leg postings — ties out exactly.</p>
      </div>
      <div class="pri">
        <span class="num">scenario-testing</span>
        <h4>A pure model runs beside the real system.</h4>
        <p>Tests pass only when the two agree. Property-based testing via fugato plus hand-authored EDN scenarios, sharing one runner.</p>
      </div>
      <div class="pri">
        <span class="num">ADR-0005</span>
        <h4>Anomalies, not exceptions.</h4>
        <p>Three semantic kinds — error, rejection, unauthorized — mapping directly to HTTP status families at every component interface.</p>
      </div>
      <div class="pri">
        <span class="num">ADR-0007</span>
        <h4>System-as-data.</h4>
        <p>donut.system + YAML. Components are records, profiles are values, testcontainers and production share one bootstrap path.</p>
      </div>
      <div class="pri">
        <span class="num">ADR-0002</span>
        <h4>The changelog <em>is</em> the outbox.</h4>
        <p>FoundationDB Record Layer gives multi-record ACID by default; the transactional outbox pattern falls out of the storage engine — no separate table.</p>
      </div>
      <div class="pri">
        <span class="num">ADR-0001</span>
        <h4>A domain fork of mono.</h4>
        <p>Infrastructure bricks live in the workspace, not as a library. Pulled upstream via <code>git merge upstream/main</code>; bank-specific code stays close.</p>
      </div>
      <div class="pri dark">
        <span class="num">REPL · TESTCONTAINERS</span>
        <h4>REPL on the inside.</h4>
        <p>Start a REPL, evaluate a comment block, and the whole system — FDB, Pulsar, HTTP — boots inside Testcontainers. The dev loop is the system.</p>
      </div>
    </div>
  </div>
</section>

<section class="final">
  <span class="ornament" aria-hidden="true">
    <Logo variant="A" size={520} idPrefix="final-orn" />
  </span>
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="eyebrow muted">Run it locally</span>
        <h2>One <em>Helm install</em> away from a chartered bank.</h2>
        <p>Install the chart, port-forward, open the SPA. Or start a REPL with <code>just repl</code> and bring the whole system up inside Testcontainers. Either way you're posting balanced transfers in minutes.</p>
        <div class="ctas">
          <button class="btn gold" onclick={onSignIn}>Sign in with Google →</button>
          <a class="btn line" href="https://github.com/repldriven/queenswood#readme">Read the quickstart</a>
        </div>
      </div>
      <div>
        <div class="code">
          <div class="bar">
            <div class="dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
            <span>install · Kubernetes</span>
          </div>
<pre class="body"><span class="c"># Install the chart (Keycloak, both consoles, all services included)</span>
<span class="k">helm</span> install queenswood \
  oci://ghcr.io/repldriven/queenswood --version <span class="g">0.1.0</span> \
  -n queenswood --create-namespace \
  --wait --timeout 10m

<span class="c"># Sign in via Keycloak at:</span>
<span class="c">#</span>   <span class="g">localhost:8081</span>  <span class="c">operator console (bank-app)</span>
<span class="c">#</span>   <span class="g">localhost:8082</span>  <span class="c">organisation console (bank-console)</span></pre>
        </div>
      </div>
    </div>
  </div>
</section>

<footer>
  <div class="wrap">
    <div class="row">
      <div class="brand-block">
        <a class="brand" href="/">
          <Logo variant="A" size={28} idPrefix="footer" />
          <span class="wm"><Wordmark variant="grotesk" size={14} /></span>
        </a>
        <p class="desc">A multi-tenant banking platform with core banking, UK Faster Payments, and tenant onboarding with IDV. Open source. MIT licensed.</p>
      </div>
      <div>
        <h5>Platform</h5>
        <ul>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/docs/prd/organizations.md">Organisations</a></li>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/docs/prd/parties.md">Parties &amp; IDV</a></li>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/docs/prd/cash-accounts.md">Cash accounts</a></li>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/docs/prd/payments.md">Payments</a></li>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/docs/prd/interest.md">Interest</a></li>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/docs/prd/policies.md">Policies</a></li>
        </ul>
      </div>
      <div>
        <h5>Developers</h5>
        <ul>
          <li><a href="https://github.com/repldriven/queenswood#readme">Docs</a></li>
          <li><a href="https://github.com/repldriven/queenswood/tree/main/docs/recipes">Recipes</a></li>
          <li><a href="https://github.com/repldriven/queenswood/tree/main/docs/adr">ADRs</a></li>
          <li><a href="https://github.com/repldriven/queenswood/releases">Releases</a></li>
        </ul>
      </div>
      <div>
        <h5>Project</h5>
        <ul>
          <li><a href="https://github.com/repldriven/queenswood">GitHub</a></li>
          <li><a href="https://github.com/repldriven/queenswood/issues">Issues</a></li>
          <li><a href="https://github.com/repldriven/queenswood/blob/main/LICENSE">MIT licence</a></li>
        </ul>
      </div>
    </div>
    <div class="meta-row">
      <span>© Queenswood · MIT licensed</span>
      <span>v0.1.0 · open source</span>
    </div>
  </div>
</footer>

<style>
  :global(html), :global(body), :global(#app) {
    background: var(--paper);
    color: var(--ink);
    font-family: var(--grotesk);
    -webkit-font-smoothing: antialiased;
  }
  :global(a) { color: inherit; text-decoration: none; }
  :global(*), :global(*::before), :global(*::after) { box-sizing: border-box; }

  .announce {
    background: var(--ink); color: var(--bone);
    font-family: var(--mono); font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase;
    padding: 10px 24px;
    display: flex; align-items: center; justify-content: center; gap: 14px;
  }
  .announce .pill {
    background: var(--gold); color: var(--ink);
    padding: 2px 8px; border-radius: 999px; font-weight: 500; font-size: 10px;
  }
  .announce a { color: var(--bone); opacity: 0.85; }
  .announce a:hover { opacity: 1; }

  .nav {
    position: sticky; top: 0; z-index: 30;
    background: rgba(251, 249, 244, 0.86); backdrop-filter: saturate(140%) blur(10px);
    border-bottom: 1px solid var(--rule-2);
  }
  .nav-inner {
    max-width: 1280px; margin: 0 auto;
    padding: 14px 32px; display: flex; align-items: center; gap: 28px;
  }
  .brand { display: flex; align-items: center; gap: 10px; }
  .brand .wm { display: inline-block; }
  .nav ul {
    list-style: none; margin: 0; padding: 0;
    display: flex; align-items: center; gap: 22px;
    font-size: 14px; color: var(--ink-2);
  }
  .nav ul li a { padding: 6px 2px; transition: color 0.15s; }
  .nav ul li a:hover { color: var(--ink); }
  .nav .spacer { flex: 1; }
  .nav .cta-row { display: flex; align-items: center; gap: 8px; }

  .btn {
    height: 36px; padding: 0 14px;
    display: inline-flex; align-items: center; gap: 8px;
    border-radius: 6px; font-size: 14px; font-weight: 500; letter-spacing: 0.005em;
    border: 1px solid transparent; cursor: pointer;
    transition: background 0.12s, border-color 0.12s, color 0.12s, transform 0.08s;
    font-family: var(--grotesk);
  }
  .btn:active { transform: translateY(0.5px); }
  .btn.ghost { color: var(--ink); background: transparent; }
  .btn.ghost:hover { background: rgba(20, 15, 10, 0.05); }
  .btn.line { border-color: var(--rule); color: var(--ink); background: transparent; }
  .btn.line:hover { background: rgba(20, 15, 10, 0.05); }
  .btn.solid { background: var(--ink); color: var(--bone); }
  .btn.solid:hover { background: #2a2622; }
  .btn.gold { background: var(--gold); color: var(--ink); }
  .btn.gold:hover { background: var(--gold-bright); }

  .wrap { max-width: 1280px; margin: 0 auto; padding: 0 32px; }

  .hero { padding: 80px 0 56px; position: relative; overflow: hidden; }
  .hero .grid { display: grid; grid-template-columns: 1.05fr 1fr; gap: 64px; align-items: center; }
  .eyebrow {
    font-family: var(--mono); font-size: 11px; letter-spacing: 0.2em; text-transform: uppercase;
    color: var(--muted); display: inline-flex; align-items: center; gap: 8px;
  }
  .eyebrow.muted { color: rgba(244, 241, 234, 0.55); }
  .eyebrow::before { content: ''; width: 18px; height: 1px; background: var(--gold-deep); }
  h1.title {
    font-family: var(--serif); font-weight: 500; font-size: 72px; line-height: 1.02;
    letter-spacing: -0.012em; margin: 18px 0 18px; max-width: 14ch; text-wrap: pretty;
  }
  h1.title em { font-style: italic; color: var(--gold-deep); font-weight: 500; }
  .lede {
    font-size: 19px; line-height: 1.55; color: var(--ink-2);
    max-width: 50ch; text-wrap: pretty;
  }
  .hero .ctas { display: flex; gap: 12px; margin-top: 28px; flex-wrap: wrap; }
  .hero .meta {
    display: flex; gap: 16px; align-items: center; margin-top: 24px;
    font-family: var(--mono); font-size: 11px; letter-spacing: 0.16em; text-transform: uppercase; color: var(--muted);
  }
  .hero .meta .dot { width: 5px; height: 5px; border-radius: 50%; background: var(--pine-3); }

  .code {
    background: var(--ink); color: #e9e3d3;
    border-radius: 12px;
    box-shadow: 0 28px 60px -28px rgba(20, 15, 10, 0.45), 0 2px 6px rgba(20, 15, 10, 0.08);
    overflow: hidden;
    font-family: var(--mono); font-size: 13.5px; line-height: 1.6;
  }
  .code .bar {
    display: flex; align-items: center; gap: 10px;
    padding: 12px 16px; border-bottom: 1px solid rgba(255, 255, 255, 0.07);
    font-family: var(--grotesk); font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase;
    color: rgba(244, 241, 234, 0.6);
  }
  .code .bar .dots { display: flex; gap: 6px; }
  .code .bar .dot { width: 9px; height: 9px; border-radius: 50%; background: rgba(255, 255, 255, 0.12); }
  .code .body { margin: 0; padding: 18px 22px; white-space: pre; overflow-x: auto; font-family: var(--mono); color: #e9e3d3; }
  .code .body .c { color: rgba(244, 241, 234, 0.42); }
  .code .body .k { color: oklch(0.86 0.08 90); }
  .code .body .s { color: oklch(0.82 0.10 145); }
  .code .body .g { color: var(--gold-bright); }

  .hero .ornament,
  .final .ornament {
    position: absolute; pointer-events: none; opacity: 0.07;
  }
  .hero .ornament { right: -120px; top: 40px; width: 460px; height: 460px; }
  .final .ornament { left: -120px; bottom: -120px; width: 520px; height: 520px; color: var(--bone); }

  .band { border-top: 1px solid var(--rule); border-bottom: 1px solid var(--rule); padding: 56px 0; }
  .band h2 {
    font-family: var(--serif); font-weight: 500; font-size: 40px; line-height: 1.12;
    letter-spacing: -0.005em; margin: 0 0 12px; max-width: 30ch; text-wrap: pretty;
  }
  .band h2 .num { color: var(--gold-deep); font-style: italic; }
  .band .row { display: grid; grid-template-columns: 1.4fr 1fr; gap: 64px; align-items: end; }
  .band .arr { font-family: var(--mono); font-size: 12px; letter-spacing: 0.14em; text-transform: uppercase; }
  .band .arr a { color: var(--ink); border-bottom: 1px solid var(--ink); padding-bottom: 2px; }

  .feat { padding: 96px 0; border-bottom: 1px solid var(--rule-2); }
  .feat.last { border-bottom: none; }
  .feat .grid { display: grid; grid-template-columns: 5fr 6fr; gap: 80px; align-items: center; }
  .feat.reverse .grid { grid-template-columns: 6fr 5fr; }
  .feat.reverse .grid > :first-child { order: 2; }
  .feat .num {
    font-family: var(--mono); font-size: 11px; letter-spacing: 0.2em; color: var(--gold-deep); text-transform: uppercase;
  }
  .feat h3 {
    font-family: var(--serif); font-weight: 500; font-size: 46px; line-height: 1.08;
    letter-spacing: -0.008em; margin: 14px 0 18px; max-width: 18ch; text-wrap: pretty;
  }
  .feat h3 em { font-style: italic; color: var(--gold-deep); }
  .feat p { font-size: 17px; line-height: 1.6; color: var(--ink-2); max-width: 50ch; margin: 0; }
  .feat ul { list-style: none; margin: 26px 0 28px; padding: 0; display: flex; flex-direction: column; gap: 10px; }
  .feat ul li { display: flex; gap: 12px; align-items: baseline; font-size: 15px; color: var(--ink-2); }
  .feat ul li::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: var(--pine-3); transform: translateY(-2px); flex: 0 0 6px; }
  .feat code { font-family: var(--mono); font-size: 13px; }

  .visual {
    background: var(--paper); border-radius: 12px; border: 1px solid var(--rule);
    box-shadow: 0 1px 0 rgba(255, 255, 255, 0.7) inset, 0 24px 60px -32px rgba(20, 15, 10, 0.30);
    overflow: hidden; aspect-ratio: 4/3; position: relative;
  }
  .visual .topbar {
    display: flex; align-items: center; gap: 10px;
    padding: 10px 14px; border-bottom: 1px solid var(--rule-2);
    background: rgba(20, 15, 10, 0.02);
    font-family: var(--mono); font-size: 11px; letter-spacing: 0.16em; text-transform: uppercase; color: var(--muted);
  }
  .visual .topbar .dots { display: flex; gap: 5px; margin-right: 6px; }
  .visual .topbar .dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(20, 15, 10, 0.18); }
  .visual .inner { padding: 22px; height: calc(100% - 38px); display: flex; flex-direction: column; gap: 14px; overflow: auto; }

  .org-table { width: 100%; border-collapse: collapse; font-family: var(--mono); font-size: 12px; }
  .org-table th, .org-table td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--rule-2); color: var(--ink-2); letter-spacing: 0.01em; }
  .org-table th { color: var(--muted); font-weight: 500; text-transform: uppercase; letter-spacing: 0.16em; font-size: 10px; }
  .org-table .status { display: inline-flex; align-items: center; gap: 6px; color: var(--pine-2); }
  .org-table .status::before { content: ''; width: 6px; height: 6px; background: var(--pine-3); border-radius: 50%; }
  .org-table .status.pending { color: var(--gold-deep); }
  .org-table .status.pending::before { background: var(--gold); }

  .trail { display: flex; flex-direction: column; gap: 0; font-family: var(--mono); font-size: 12px; }
  .trail .step { display: grid; grid-template-columns: 70px 24px 1fr 90px; gap: 12px; padding: 9px 0; align-items: center; border-bottom: 1px dashed var(--rule-2); }
  .trail .step:last-child { border-bottom: 0; }
  .trail .step .t { color: var(--muted); font-size: 11px; }
  .trail .step .pin { width: 10px; height: 10px; border-radius: 50%; background: var(--paper); border: 2px solid var(--pine-3); }
  .trail .step.done .pin { background: var(--pine-2); border-color: var(--pine-2); }
  .trail .step.active .pin { background: var(--gold); border-color: var(--gold-deep); box-shadow: 0 0 0 4px rgba(170, 120, 30, 0.12); }
  .trail .step .desc { color: var(--ink); }
  .trail .step .kind { color: var(--muted); font-size: 11px; text-align: right; }
  .trail .step .desc .ref { color: var(--muted); font-size: 11px; margin-left: 6px; }

  .accrual { display: grid; grid-template-columns: 1fr; gap: 8px; font-family: var(--mono); font-size: 12px; }
  .accrual .day {
    display: grid; grid-template-columns: 70px 1fr 130px 80px;
    gap: 12px; padding: 7px 10px; align-items: center;
    border-bottom: 1px solid var(--rule-2);
  }
  .accrual .day.cap { background: rgba(170, 120, 30, 0.06); border-radius: 6px; border-bottom: 0; padding: 10px; }
  .accrual .day .lab { color: var(--muted); font-size: 10px; letter-spacing: 0.16em; text-transform: uppercase; }
  .accrual .day .micro { color: var(--ink); }
  .accrual .day .cap-day { color: var(--gold-deep); font-weight: 500; }
  .accrual .day .carry { color: var(--gold-deep); font-size: 11px; }
  .accrual .day .post { color: var(--pine-2); text-align: right; }

  .principles { padding: 96px 0; border-bottom: 1px solid var(--rule-2); }
  .principles h2 {
    font-family: var(--serif); font-weight: 500; font-size: 50px; line-height: 1.06;
    letter-spacing: -0.008em; margin: 8px 0 14px; max-width: 22ch; text-wrap: pretty;
  }
  .principles h2 em { font-style: italic; color: var(--gold-deep); }
  .principles .lead { font-size: 17px; color: var(--ink-2); max-width: 56ch; margin: 0 0 48px; line-height: 1.55; }
  .principles .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 28px; }
  .pri {
    padding: 26px; border: 1px solid var(--rule); border-radius: 12px; background: var(--paper);
    display: flex; flex-direction: column; gap: 10px; min-height: 200px;
    position: relative;
  }
  .pri .num { font-family: var(--mono); font-size: 11px; letter-spacing: 0.2em; color: var(--gold-deep); text-transform: uppercase; }
  .pri h4 { font-family: var(--serif); font-size: 24px; font-weight: 500; margin: 0; line-height: 1.15; letter-spacing: -0.005em; text-wrap: pretty; }
  .pri h4 em { font-style: italic; color: var(--gold-deep); }
  .pri p { margin: 0; font-size: 14px; line-height: 1.55; color: var(--ink-2); }
  .pri code { font-family: var(--mono); font-size: 12.5px; }
  .pri.dark { background: var(--ink); color: var(--bone); border-color: var(--ink); }
  .pri.dark .num { color: var(--gold); }
  .pri.dark h4 { color: var(--bone); }
  .pri.dark p { color: rgba(244, 241, 234, 0.7); }

  .final { background: var(--ink); color: var(--bone); padding: 96px 0; position: relative; overflow: hidden; }
  .final h2 { font-family: var(--serif); font-weight: 500; font-size: 60px; line-height: 1.04; letter-spacing: -0.01em; margin: 0 0 14px; max-width: 18ch; text-wrap: pretty; }
  .final h2 em { color: var(--gold); font-style: italic; }
  .final p { color: rgba(244, 241, 234, 0.7); font-size: 17px; line-height: 1.55; max-width: 52ch; margin: 0 0 32px; }
  .final code { font-family: var(--mono); font-size: 14px; }
  .final .grid { display: grid; grid-template-columns: 1.05fr 1fr; gap: 80px; align-items: center; }
  .final .ctas { display: flex; gap: 12px; flex-wrap: wrap; }
  .final .btn.line { border-color: rgba(244, 241, 234, 0.25); color: var(--bone); background: transparent; }
  .final .btn.line:hover { background: rgba(244, 241, 234, 0.06); }

  footer { padding: 64px 0 40px; color: var(--ink-2); background: var(--paper); }
  footer .row { display: grid; grid-template-columns: 2fr repeat(3, 1fr); gap: 40px; padding-bottom: 40px; border-bottom: 1px solid var(--rule); }
  footer h5 { font-family: var(--mono); font-size: 11px; letter-spacing: 0.2em; text-transform: uppercase; color: var(--muted); font-weight: 500; margin: 0 0 16px; }
  footer ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; font-size: 14px; }
  footer ul a:hover { color: var(--ink); }
  footer .brand-block .desc { margin-top: 14px; color: var(--muted); font-size: 13px; line-height: 1.55; max-width: 32ch; }
  footer .meta-row { padding-top: 20px; display: flex; justify-content: space-between; align-items: center; font-family: var(--mono); font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase; color: var(--muted); }
</style>
