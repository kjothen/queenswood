<script>
  /* Marketing-style landing for the unauthenticated bank-console.
     Structure ported from design_handoff_queenswood_logo/Queenswood Home.html.
     The three "Sign in" CTAs route to /#/sign-in, where the user picks
     an identity provider. Keycloak handoff happens from SignInPage. */

  import { push } from "svelte-spa-router";
  import {
    Logo,
    Wordmark,
    ThemeToggle,
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    CodeCard,
  } from "@queenswood/bank-ui";
  import DocViewer from "./DocViewer.svelte";

  // Slug helper for the CardFooter ref link: trims the GitHub URL down
  // to the doc's human-readable slug (strips path, leading numbers,
  // and the .md extension). "0013-single-unified-api.md" → "single-
  // unified-api"; "policy-evaluation.md" → "policy-evaluation".
  function slugFromHref(href) {
    const file = href.split("/").pop() ?? "";
    return file.replace(/\.md$/i, "").replace(/^\d+-/, "");
  }

  // The Engineering Choices grid. Last card has variant="feature" so
  // it stands out as the section's one inverted accent. Body uses
  // {@html} because one entry contains inline <code>.
  const PRINCIPLES = [
    { key: "adr-0013", kicker: "ADR · 0013 · 0014",
      title: "One unified API. OpenAPI is the contract.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/adr/0013-single-unified-api.md",
      body: "Bank-shaped, not implementation-shaped. The spec drives client generation, validation, and documentation — there is no second source of truth." },
    { key: "tdd-policy", kicker: "TDD · policy-evaluation",
      title: "Policies as data, not hard-coded rules.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/tdd/policy-evaluation.md",
      body: "Capabilities and limits are records. A curative-permit pattern lets your customers self-correct balances out of breach without a manual override." },
    { key: "tdd-interest", kicker: "TDD · interest",
      title: "Pennies are conserved by construction.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/tdd/interest.md",
      body: "Integer micro-unit arithmetic with sub-minor-unit carry. Daily accrual, monthly capitalisation, six-leg postings — ties out exactly." },
    { key: "tdd-scenario", kicker: "TDD · scenario-testing",
      title: "A pure model runs beside the real system.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/tdd/scenario-testing.md",
      body: "Tests pass only when the two agree. Property-based testing via fugato plus hand-authored EDN scenarios, sharing one runner." },
    { key: "adr-0005", kicker: "ADR · 0005",
      title: "Anomalies, not exceptions.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/adr/0005-error-handling-with-anomalies.md",
      body: "Three semantic kinds — error, rejection, unauthorized — mapping directly to HTTP status families at every component interface." },
    { key: "adr-0007", kicker: "ADR · 0007",
      title: "System-as-data.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/adr/0007-system-as-data.md",
      body: "donut.system + YAML. Components are records, profiles are values, testcontainers and production share one bootstrap path." },
    { key: "adr-0002", kicker: "ADR · 0002",
      title: "The changelog is the outbox.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/adr/0002-foundationdb-record-layer.md",
      body: "FoundationDB Record Layer gives multi-record ACID by default; the transactional outbox pattern falls out of the storage engine — no separate table." },
    { key: "adr-0001", kicker: "ADR · 0001",
      title: "A domain fork of mono.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/adr/0001-reuse-mono-as-upstream.md",
      body: "Infrastructure bricks live in the workspace, not as a library. Pulled upstream via <code>git merge upstream/main</code>; bank-specific code stays close." },
    { key: "recipe-tc", kicker: "Recipe · testcontainers",
      title: "REPL on the inside.",
      href: "https://github.com/repldriven/queenswood/blob/main/docs/recipes/testcontainers.md",
      body: "Start a REPL, evaluate a comment block, and the whole system — FDB, Pulsar, HTTP, Keycloak — boots inside Testcontainers. The dev loop is the system.",
      variant: "feature" },
  ];

  // Markdown docs imported as raw strings at build time. Each card
  // in the Engineering Choices section opens its doc in a modal via
  // `DocViewer`; plain clicks intercept and open the modal, modified
  // clicks (cmd / ctrl / shift / middle) fall through to the github
  // anchor in a new tab.
  import adr0013 from "../../../../docs/adr/0013-single-unified-api.md?raw";
  import tddPolicy from "../../../../docs/tdd/policy-evaluation.md?raw";
  import tddInterest from "../../../../docs/tdd/interest.md?raw";
  import tddScenario from "../../../../docs/tdd/scenario-testing.md?raw";
  import adr0005 from "../../../../docs/adr/0005-error-handling-with-anomalies.md?raw";
  import adr0007 from "../../../../docs/adr/0007-system-as-data.md?raw";
  import adr0002 from "../../../../docs/adr/0002-foundationdb-record-layer.md?raw";
  import adr0001 from "../../../../docs/adr/0001-reuse-mono-as-upstream.md?raw";
  import recipeTC from "../../../../docs/recipes/testcontainers.md?raw";

  const docs = {
    "adr-0013": {
      raw: adr0013,
      path: "docs/adr/0013-single-unified-api.md",
      label: "ADR · 0013 · 0014",
    },
    "tdd-policy": {
      raw: tddPolicy,
      path: "docs/tdd/policy-evaluation.md",
      label: "TDD · policy-evaluation",
    },
    "tdd-interest": {
      raw: tddInterest,
      path: "docs/tdd/interest.md",
      label: "TDD · interest",
    },
    "tdd-scenario": {
      raw: tddScenario,
      path: "docs/tdd/scenario-testing.md",
      label: "TDD · scenario-testing",
    },
    "adr-0005": {
      raw: adr0005,
      path: "docs/adr/0005-error-handling-with-anomalies.md",
      label: "ADR · 0005",
    },
    "adr-0007": {
      raw: adr0007,
      path: "docs/adr/0007-system-as-data.md",
      label: "ADR · 0007",
    },
    "adr-0002": {
      raw: adr0002,
      path: "docs/adr/0002-foundationdb-record-layer.md",
      label: "ADR · 0002",
    },
    "adr-0001": {
      raw: adr0001,
      path: "docs/adr/0001-reuse-mono-as-upstream.md",
      label: "ADR · 0001",
    },
    "recipe-tc": {
      raw: recipeTC,
      path: "docs/recipes/testcontainers.md",
      label: "Recipe · testcontainers",
    },
  };

  let openDocKey = $state(null);

  function openDoc(key) {
    return (e) => {
      // Let cmd/ctrl/shift/middle-click fall through to the anchor's
      // default — opens GitHub in a new tab the way a power user expects.
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
      e.preventDefault();
      openDocKey = key;
    };
  }

  function goSignIn() {
    push("/sign-in");
  }
</script>

<DocViewer
  doc={openDocKey ? docs[openDocKey] : null}
  onClose={() => (openDocKey = null)}
/>

<div class="announce">
  <span class="pill">v0.1.0</span>
  <span>
    Bring your own <strong>ClearBank</strong> and <strong>Onfido</strong> — bundled
    simulators for development, plug in your own accounts per organisation.
  </span>
  <a target="_blank" rel="noreferrer"
    href="https://github.com/repldriven/queenswood/tree/main/bases/bank-clearbank-adapter"
    >ClearBank adapter ↗</a
  >
  <a target="_blank" rel="noreferrer"
    href="https://github.com/repldriven/queenswood/tree/main/bases/bank-onfido-adapter"
    >Onfido adapter ↗</a
  >
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
      <li><a target="_blank" rel="noreferrer" href="https://github.com/repldriven/queenswood">GitHub</a></li>
    </ul>
    <div class="spacer"></div>
    <div class="cta-row">
      <ThemeToggle />
      <button class="btn solid" onclick={goSignIn}>Sign in</button>
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
        <h1 class="title">
          Core banking,<br /><em>modernized.</em>
        </h1>
        <p class="lede">
          Everything a modern fintech needs to operate as a bank — double-entry
          ledgers, UK Faster Payments, customer KYC — under one unified OpenAPI.
          Use the hosted edition, or self-host the open core. MIT-licensed.
        </p>
        <div class="ctas">
          <button class="btn solid" onclick={goSignIn}>Sign in</button>
          <a target="_blank" rel="noreferrer"
            class="btn line"
            href="https://github.com/repldriven/queenswood#readme"
            >Read the docs</a
          >
          <a target="_blank" rel="noreferrer" class="btn ghost" href="https://github.com/repldriven/queenswood"
            >View on GitHub ↗</a
          >
        </div>
        <div class="meta">
          <span class="dot"></span>
          <span>UK&nbsp;FPS&nbsp;Simulator</span>
          <span class="dot"></span>
          <span>IDV&nbsp;Simulator</span>
          <span class="dot"></span>
          <span>OAuth</span>
          <span class="dot"></span>
          <span>OpenAPI&nbsp;3.x</span>
        </div>
      </div>
      <div>
        <CodeCard filename="~/queenswood · zsh">
          <pre><span class="syn-comment"># Authed via OAuth (Keycloak). Issue an API key for machine-to-machine.</span>
<span class="syn-keyword">curl</span> -X POST https://api.queenswood.local/v1/organisations \
  -H <span class="syn-string">"Authorization: Bearer $QW_OAUTH_TOKEN"</span> \
  -H <span class="syn-string">"Content-Type: application/json"</span> \
  -d <span class="syn-string">{`'{ "name": "northwind-fs",
       "jurisdiction": "GB" }'`}</span>

<span class="syn-comment">#</span> <span class="syn-emphasis">{`{ "id": "org.01HW7…",`}</span>
<span class="syn-comment">#  </span>  <span class="syn-emphasis">{`"status": "active" }`}</span></pre>
        </CodeCard>
      </div>
    </div>
  </div>
</section>

<section id="platform" class="capabilities">
  <div class="wrap">
    <div class="cap-grid">
      <div class="cap">
        <span class="cap-label">Core ledger</span>
        <p class="cap-desc">Double-entry postings, ties to the penny.</p>
      </div>
      <div class="cap">
        <span class="cap-label">Multi-tenant</span>
        <p class="cap-desc">Onboard, isolate, bill per organisation.</p>
      </div>
      <div class="cap">
        <span class="cap-label">KYC</span>
        <p class="cap-desc">Onfido IDV for your customers.</p>
      </div>
      <div class="cap">
        <span class="cap-label">UK payments</span>
        <p class="cap-desc">Faster Payments via ClearBank.</p>
      </div>
      <div class="cap">
        <span class="cap-label">Interest</span>
        <p class="cap-desc">Accrue and capitalise on your cadence.</p>
      </div>
      <div class="cap">
        <span class="cap-label">Policies</span>
        <p class="cap-desc">Capabilities and limits as data.</p>
      </div>
    </div>
  </div>
</section>

<section class="feat">
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="num">01 — Up and Running</span>
        <h3>Sign in. <em>Spin up a bank.</em> Go.</h3>
        <p>
          Humans sign in through Keycloak — OAuth, SSO, social login, whatever
          your IdP supports. They land in their console, charter an
          organisation, and issue API keys for the services that need them.
          Operators get a separate console for platform-wide policy and review.
        </p>
        <ul>
          <li>Two consoles — one for Queenswood operators, one for bank teams</li>
          <li>OAuth via bundled Keycloak — swap in your own IdP at deploy</li>
          <li>
            API keys for machine-to-machine — returned once, stored hashed
          </li>
          <li>
            Capabilities &amp; limits as editable policy data, not hard-coded
            rules
          </li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots">
            <span class="dot"></span><span class="dot"></span><span class="dot"
            ></span>
          </div>
          console / organisations
        </div>
        <div class="inner">
          <table class="org-table">
            <thead
              ><tr
                ><th>Bank</th><th>Created</th><th>Cash accts</th><th>Status</th
                ></tr
              ></thead
            >
            <tbody>
              <tr
                ><td>org.01HW7Z…</td><td>just now</td><td>0</td><td
                  ><span class="status">Active</span></td
                ></tr
              >
              <tr
                ><td>org.01HW5K…</td><td>3 days ago</td><td>1,284</td><td
                  ><span class="status">Active</span></td
                ></tr
              >
              <tr
                ><td>org.01HVTM…</td><td>1 week ago</td><td>602</td><td
                  ><span class="status">Active</span></td
                ></tr
              >
              <tr
                ><td>org.01HVQ4…</td><td>2 weeks ago</td><td>14</td><td
                  ><span class="status pending">Onboarding</span></td
                ></tr
              >
              <tr
                ><td>org.01HV9P…</td><td>1 month ago</td><td>3,901</td><td
                  ><span class="status">Active</span></td
                ></tr
              >
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
        <span class="num">02 — KYC</span>
        <h3>Identity in. <em>Account active.</em></h3>
        <p>
          Every person-party goes through IDV before their account activates. A <code
            >bank-idv</code
          > watcher fires the Onfido check on party creation; the webhook flips the
          party to active (or rejected) automatically — no operator click.
        </p>
        <ul>
          <li>Bring your own Onfido — simulator bundled for dev &amp; tests</li>
          <li>Standard Onfido reports: document + facial similarity</li>
          <li>Webhook → party activates (or rejects) automatically</li>
          <li>Per-party audit trail of IDV submissions and outcomes</li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots">
            <span class="dot"></span><span class="dot"></span><span class="dot"
            ></span>
          </div>
          idv / pty.01HW9X…
        </div>
        <div class="inner">
          <div class="trail">
            <div class="step done">
              <span class="t">09:14:21</span>
              <span class="pin"></span>
              <span class="desc"
                >party-created<span class="ref"
                  >Arthur Dent · status pending</span
                ></span
              >
              <span class="kind">api</span>
            </div>
            <div class="step done">
              <span class="t">09:14:21</span>
              <span class="pin"></span>
              <span class="desc"
                >submit-idv-check<span class="ref">scheme channel · Onfido</span
                ></span
              >
              <span class="kind">pulsar</span>
            </div>
            <div class="step done">
              <span class="t">09:14:22</span>
              <span class="pin"></span>
              <span class="desc"
                >applicant-created<span class="ref">Onfido id 9b6e8d8f…</span
                ></span
              >
              <span class="kind">onfido</span>
            </div>
            <div class="step active">
              <span class="t">09:14:24</span>
              <span class="pin"></span>
              <span class="desc"
                >check-completed<span class="ref"
                  >document + facial · clear</span
                ></span
              >
              <span class="kind">webhook</span>
            </div>
            <div class="step">
              <span class="t">—</span>
              <span class="pin"></span>
              <span class="desc">party-activated</span>
              <span class="kind">api</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="feat">
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="num">03 — Payments</span>
        <h3>UK Faster Payments. <em>Double-entry,</em> all the way down.</h3>
        <p>
          Internal transfers and outbound FPS via a pluggable scheme adapter.
          Inbound settlement with BBAN lookup and idempotency. Every transfer is
          a balanced pair of postings against typed accounts — every step a
          typed event your team can observe.
        </p>
        <ul>
          <li>SCAN payment addresses — sort code &amp; account number</li>
          <li>
            ClearBank adapter for FPS, with a simulator for dev &amp; tests
          </li>
          <li>
            Idempotent submit — duplicate requests are safe by construction
          </li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots">
            <span class="dot"></span><span class="dot"></span><span class="dot"
            ></span>
          </div>
          payment / pmt.01HW8Z…
        </div>
        <div class="inner">
          <div class="trail">
            <div class="step done">
              <span class="t">09:41:02</span>
              <span class="pin"></span>
              <span class="desc"
                >payment-submitted<span class="ref"
                  >12,400.00 GBP → 04-00-04 / 12345678</span
                ></span
              >
              <span class="kind">api</span>
            </div>
            <div class="step done">
              <span class="t">09:41:02</span>
              <span class="pin"></span>
              <span class="desc"
                >policy-evaluated<span class="ref"
                  >limits ok · curative-permit not required</span
                ></span
              >
              <span class="kind">policy</span>
            </div>
            <div class="step done">
              <span class="t">09:41:03</span>
              <span class="pin"></span>
              <span class="desc"
                >submit-payment<span class="ref"
                  >scheme channel · ClearBank</span
                ></span
              >
              <span class="kind">pulsar</span>
            </div>
            <div class="step active">
              <span class="t">09:41:04</span>
              <span class="pin"></span>
              <span class="desc"
                >transaction-settled<span class="ref"
                  >FPS webhook · two postings written</span
                ></span
              >
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

<section class="feat reverse last">
  <div class="wrap">
    <div class="grid">
      <div>
        <span class="num">04 — Interest</span>
        <h3>Daily accrual that <em>conserves pennies.</em></h3>
        <p>
          Integer micro-unit arithmetic with sub-minor-unit carry — fractional
          interest accrues precisely every day, and capitalisation ties to the
          penny. Cadence is the operator's choice.
        </p>
        <ul>
          <li>Micro-unit precision · no floating point anywhere</li>
          <li>Capitalisation that ties to the penny exactly</li>
          <li>Configurable per cash-account product, versioned at publish</li>
        </ul>
      </div>
      <div class="visual">
        <div class="topbar">
          <div class="dots">
            <span class="dot"></span><span class="dot"></span><span class="dot"
            ></span>
          </div>
          interest / acc.treasury-01
        </div>
        <div class="inner">
          <div class="accrual">
            <div class="day">
              <span class="lab">DAY</span><span class="lab">Balance · GBP</span
              ><span class="lab">Accrued · GBP</span><span class="lab"
                >Carry · μGBP</span
              >
            </div>
            <div class="day">
              <span class="micro">01 May</span><span class="micro"
                >100,000.00</span
              ><span class="micro">13.69</span><span class="carry"
                >+863,013</span
              >
            </div>
            <div class="day">
              <span class="micro">02 May</span><span class="micro"
                >100,000.00</span
              ><span class="micro">13.70</span><span class="carry"
                >+726,026</span
              >
            </div>
            <div class="day">
              <span class="micro">03 May</span><span class="micro"
                >100,000.00</span
              ><span class="micro">13.70</span><span class="carry"
                >+589,039</span
              >
            </div>
            <div class="day">
              <span class="micro">…</span><span class="micro"
                >stable · 100k</span
              ><span class="micro">31 days · 424.66</span><span class="carry"
                >+175,342</span
              >
            </div>
            <div class="day cap">
              <span class="micro cap-day">31 May · CAP</span>
              <span class="micro">capitalised</span>
              <span class="post">+424.66 GBP</span>
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
    <h2>
      The interesting bits — <em>for engineers</em> who'd actually read the docs.
    </h2>
    <p class="lead">
      Queenswood is opinionated. Key choices that show up everywhere in the
      codebase, each documented, so you can read on a coffee break.
    </p>
    <div class="grid">
      {#each PRINCIPLES as p (p.key)}
        <Card
          variant={p.variant}
          href={p.href}
          onclick={openDoc(p.key)}
          target="_blank"
          rel="noreferrer"
        >
          <CardHeader kicker={p.kicker} title={p.title} />
          <CardBody>
            {@html p.body}
          </CardBody>
          <CardFooter><a href={p.href}>{slugFromHref(p.href)} →</a></CardFooter>
        </Card>
      {/each}
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
        <h2>One install away from a <em>working bank.</em></h2>
        <p>
          Install the chart, port-forward, open the SPA. Or start a REPL with <code
            >just repl</code
          > and bring the whole system up inside Testcontainers. Either way you're
          posting balanced transfers in minutes.
        </p>
        <div class="ctas">
          <button class="btn gold" onclick={goSignIn}>Sign in</button>
          <a target="_blank" rel="noreferrer"
            class="btn line"
            href="https://github.com/repldriven/queenswood#readme"
            >Read the quickstart</a
          >
        </div>
      </div>
      <div>
        <CodeCard filename="install · Kubernetes">
          <pre><span class="syn-comment"># Install the chart (Keycloak, both consoles, all services included)</span>
<span class="syn-keyword">helm</span> install queenswood \
  oci://ghcr.io/repldriven/queenswood \
  -n queenswood --create-namespace \
  --wait --timeout 10m

<span class="syn-comment"># Sign in via Keycloak at:</span>
<span class="syn-comment">#</span>   <span class="syn-emphasis">localhost:8081</span>  <span class="syn-comment">for Queenswood operators</span>
<span class="syn-comment">#</span>   <span class="syn-emphasis">localhost:8082</span>  <span class="syn-comment">for bank teams</span></pre>
        </CodeCard>
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
        <p class="desc">
          Core banking, modernised. Bring your own ClearBank and Onfido —
          open source under MIT, with bundled simulators for development.
        </p>
      </div>
      <div>
        <h5>Product</h5>
        <ul>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/blob/main/docs/prd/onboarding.md"
              >Onboarding</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/blob/main/docs/prd/parties.md"
              >Parties &amp; IDV</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/blob/main/docs/prd/cash-accounts.md"
              >Cash accounts</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/blob/main/docs/prd/payments.md"
              >Payments</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/blob/main/docs/prd/interest.md"
              >Interest</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/blob/main/docs/prd/policies.md"
              >Policies</a
            >
          </li>
        </ul>
      </div>
      <div>
        <h5>Developers</h5>
        <ul>
          <li>
            <a target="_blank" rel="noreferrer" href="https://github.com/repldriven/queenswood#readme">Docs</a>
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/tree/main/docs/recipes"
              >Recipes</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer"
              href="https://github.com/repldriven/queenswood/tree/main/docs/adr"
              >ADRs</a
            >
          </li>
          <li>
            <a target="_blank" rel="noreferrer" href="https://github.com/repldriven/queenswood/releases"
              >Releases</a
            >
          </li>
        </ul>
      </div>
      <div>
        <h5>Project</h5>
        <ul>
          <li><a target="_blank" rel="noreferrer" href="https://github.com/repldriven/queenswood">GitHub</a></li>
          <li>
            <a target="_blank" rel="noreferrer" href="https://github.com/repldriven/queenswood/issues">Issues</a>
          </li>
          <li>
            <a target="_blank" rel="noreferrer" href="https://github.com/repldriven/queenswood/blob/main/LICENSE"
              >MIT licence</a
            >
          </li>
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
  :global(html),
  :global(body),
  :global(#app) {
    /* Landing sits on --surface (the base tier), matching the
       showcase. Cards and code panels live *above* this baseline
       on --surface-raised / --ink, so they read as elevated /
       depressed in both themes. Originally migrated to
       --surface-raised here too, which squashed CodeCard against
       the page in dark mode. */
    background: var(--surface);
    color: var(--fg);
    font-family: var(--grotesk);
    -webkit-font-smoothing: antialiased;
  }
  :global(a) {
    color: inherit;
    text-decoration: none;
  }
  :global(*),
  :global(*::before),
  :global(*::after) {
    box-sizing: border-box;
  }

  /* Always-dark punctuation bar at the very top: ink in both light
     and dark, with bone text. Doesn't theme — it's a constant accent
     element by design. */
  .announce {
    background: var(--ink);
    color: var(--bone);
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    padding: 10px 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 14px;
  }
  .announce .pill {
    background: var(--gold);
    color: var(--ink);
    padding: 2px 8px;
    border-radius: 999px;
    font-weight: 500;
    font-size: 10px;
  }
  .announce a {
    color: var(--bone);
    opacity: 0.85;
  }
  .announce a:hover {
    opacity: 1;
  }
  .announce strong {
    color: var(--gold);
    font-weight: 500;
  }
  @media (max-width: 960px) {
    .announce {
      flex-direction: column;
      gap: 8px;
      padding: 12px 16px;
      text-align: center;
    }
  }

  .nav {
    position: sticky;
    top: 0;
    z-index: 30;
    background: var(--surface-translucent);
    backdrop-filter: saturate(140%) blur(10px);
    border-bottom: 1px solid var(--rule-2);
  }
  .nav-inner {
    max-width: 1280px;
    margin: 0 auto;
    padding: 14px 32px;
    display: flex;
    align-items: center;
    gap: 28px;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .brand .wm {
    display: inline-block;
  }
  .nav ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    align-items: center;
    gap: 22px;
    font-size: 14px;
    color: var(--fg-2);
  }
  .nav ul li a {
    padding: 6px 2px;
    transition: color 0.15s;
  }
  .nav ul li a:hover {
    color: var(--fg);
  }
  .nav .spacer {
    flex: 1;
  }
  .nav .cta-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .btn {
    height: 36px;
    padding: 0 14px;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 500;
    letter-spacing: 0.005em;
    border: 1px solid transparent;
    cursor: pointer;
    transition:
      background 0.12s,
      border-color 0.12s,
      color 0.12s,
      transform 0.08s;
    font-family: var(--grotesk);
  }
  .btn:active {
    transform: translateY(0.5px);
  }
  .btn.ghost {
    color: var(--fg);
    background: transparent;
  }
  .btn.ghost:hover {
    background: var(--hover-overlay);
  }
  .btn.line {
    border-color: var(--rule);
    color: var(--fg);
    background: transparent;
  }
  .btn.line:hover {
    background: var(--hover-overlay);
  }
  .btn.solid {
    background: var(--fg);
    color: var(--surface);
  }
  .btn.solid:hover {
    /* Slightly-different shade of fg; inverts cleanly in dark mode
       where the button background is bone and this becomes bone-2. */
    background: var(--fg-2);
  }
  .btn.gold {
    background: var(--gold);
    /* Gold is constant across themes, so the text on it must be
       constant too — raw ink for high contrast in both modes. */
    color: var(--ink);
  }
  .btn.gold:hover {
    background: var(--gold-bright);
  }

  .wrap {
    max-width: 1280px;
    margin: 0 auto;
    padding: 0 32px;
  }

  .hero {
    padding: 80px 0 56px;
    position: relative;
    overflow: hidden;
  }
  .hero .grid {
    display: grid;
    grid-template-columns: 1.05fr 1fr;
    gap: 64px;
    align-items: center;
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
  .eyebrow.muted {
    color: rgba(244, 241, 234, 0.55);
  }
  .eyebrow::before {
    content: "";
    width: 18px;
    height: 1px;
    background: var(--gold-deep);
  }
  h1.title {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 72px;
    line-height: 1.02;
    letter-spacing: -0.012em;
    margin: 18px 0 18px;
    max-width: 14ch;
    text-wrap: pretty;
  }
  h1.title em {
    font-style: italic;
    color: var(--gold-deep);
    font-weight: 500;
  }
  .lede {
    font-size: 19px;
    line-height: 1.55;
    color: var(--fg-2);
    max-width: 50ch;
    text-wrap: pretty;
  }
  .hero .ctas {
    display: flex;
    gap: 12px;
    margin-top: 28px;
    flex-wrap: wrap;
  }
  .hero .meta {
    display: flex;
    gap: 16px;
    align-items: center;
    margin-top: 24px;
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .hero .meta .dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--pine-3);
  }

  /* Terminal/code panels and ADR cards are now CodeCard + Card from
     bank-ui; the chrome/typography lives in the design system. */

  .hero .ornament,
  .final .ornament {
    position: absolute;
    pointer-events: none;
    opacity: 0.07;
  }
  .hero .ornament {
    right: -120px;
    top: 40px;
    width: 460px;
    height: 460px;
  }
  .final .ornament {
    left: -120px;
    bottom: -120px;
    width: 520px;
    height: 520px;
    color: var(--surface);
  }

  .capabilities {
    border-top: 1px solid var(--rule);
    border-bottom: 1px solid var(--rule);
    padding: 56px 0;
  }
  .cap-grid {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 0;
  }
  .cap {
    padding: 0 20px;
    border-left: 1px solid var(--rule-2);
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .cap:first-child {
    border-left: 0;
    padding-left: 0;
  }
  .cap:last-child {
    padding-right: 0;
  }
  .cap-label {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--gold-deep);
  }
  .cap-desc {
    margin: 0;
    font-size: 14px;
    line-height: 1.5;
    color: var(--fg-2);
    text-wrap: pretty;
  }
  @media (max-width: 1024px) {
    .cap-grid {
      grid-template-columns: repeat(3, 1fr);
      row-gap: 32px;
    }
    .cap:nth-child(3n + 1) {
      border-left: 0;
      padding-left: 0;
    }
  }
  @media (max-width: 640px) {
    .cap-grid {
      grid-template-columns: repeat(2, 1fr);
    }
    .cap {
      border-left: 0;
      padding-left: 0;
    }
    .cap:nth-child(3n + 1) {
      border-left: 0;
    }
  }

  .feat {
    padding: 96px 0;
    border-bottom: 1px solid var(--rule-2);
  }
  .feat.last {
    border-bottom: none;
  }
  .feat .grid {
    display: grid;
    grid-template-columns: 5fr 6fr;
    gap: 80px;
    align-items: center;
  }
  .feat.reverse .grid {
    grid-template-columns: 6fr 5fr;
  }
  .feat.reverse .grid > :first-child {
    order: 2;
  }
  .feat .num {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    color: var(--gold-deep);
    text-transform: uppercase;
  }
  .feat h3 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 46px;
    line-height: 1.08;
    letter-spacing: -0.008em;
    margin: 14px 0 18px;
    max-width: 18ch;
    text-wrap: pretty;
  }
  .feat h3 em {
    font-style: italic;
    color: var(--gold-deep);
  }
  .feat p {
    font-size: 17px;
    line-height: 1.6;
    color: var(--fg-2);
    max-width: 50ch;
    margin: 0;
  }
  .feat ul {
    list-style: none;
    margin: 26px 0 28px;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .feat ul li {
    display: flex;
    gap: 12px;
    align-items: baseline;
    font-size: 15px;
    color: var(--fg-2);
  }
  .feat ul li::before {
    content: "";
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--pine-3);
    transform: translateY(-2px);
    flex: 0 0 6px;
  }
  .feat code {
    font-family: var(--mono);
    font-size: 13px;
  }

  .visual {
    background: var(--surface-raised);
    border-radius: 12px;
    border: 1px solid var(--rule);
    box-shadow:
      0 1px 0 rgba(255, 255, 255, 0.7) inset,
      0 24px 60px -32px rgba(20, 15, 10, 0.3);
    overflow: hidden;
    aspect-ratio: 4/3;
    position: relative;
  }
  .visual .topbar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    border-bottom: 1px solid var(--rule-2);
    background: rgba(20, 15, 10, 0.02);
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .visual .topbar .dots {
    display: flex;
    gap: 5px;
    margin-right: 6px;
  }
  .visual .topbar .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: rgba(20, 15, 10, 0.18);
  }
  .visual .inner {
    padding: 22px;
    height: calc(100% - 38px);
    display: flex;
    flex-direction: column;
    gap: 14px;
    overflow: auto;
  }

  .org-table {
    width: 100%;
    border-collapse: collapse;
    font-family: var(--mono);
    font-size: 12px;
  }
  .org-table th,
  .org-table td {
    text-align: left;
    padding: 10px 12px;
    border-bottom: 1px solid var(--rule-2);
    color: var(--fg-2);
    letter-spacing: 0.01em;
  }
  .org-table th {
    color: var(--fg-muted);
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.16em;
    font-size: 10px;
  }
  .org-table .status {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--pine-2);
  }
  .org-table .status::before {
    content: "";
    width: 6px;
    height: 6px;
    background: var(--pine-3);
    border-radius: 50%;
  }
  .org-table .status.pending {
    color: var(--gold-deep);
  }
  .org-table .status.pending::before {
    background: var(--gold);
  }

  .trail {
    display: flex;
    flex-direction: column;
    gap: 0;
    font-family: var(--mono);
    font-size: 12px;
  }
  .trail .step {
    display: grid;
    grid-template-columns: 70px 24px 1fr 90px;
    gap: 12px;
    padding: 9px 0;
    align-items: center;
    border-bottom: 1px dashed var(--rule-2);
  }
  .trail .step:last-child {
    border-bottom: 0;
  }
  .trail .step .t {
    color: var(--fg-muted);
    font-size: 11px;
  }
  .trail .step .pin {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--surface-raised);
    border: 2px solid var(--pine-3);
  }
  .trail .step.done .pin {
    background: var(--pine-2);
    border-color: var(--pine-2);
  }
  .trail .step.active .pin {
    background: var(--gold);
    border-color: var(--gold-deep);
    box-shadow: 0 0 0 4px rgba(170, 120, 30, 0.12);
  }
  .trail .step .desc {
    color: var(--fg);
  }
  .trail .step .kind {
    color: var(--fg-muted);
    font-size: 11px;
    text-align: right;
  }
  .trail .step .desc .ref {
    color: var(--fg-muted);
    font-size: 11px;
    margin-left: 6px;
  }

  .accrual {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
    font-family: var(--mono);
    font-size: 12px;
  }
  .accrual .day {
    display: grid;
    grid-template-columns: 70px 1fr 130px 80px;
    gap: 12px;
    padding: 7px 10px;
    align-items: center;
    border-bottom: 1px solid var(--rule-2);
  }
  .accrual .day.cap {
    background: rgba(170, 120, 30, 0.06);
    border-radius: 6px;
    border-bottom: 0;
    padding: 10px;
  }
  .accrual .day .lab {
    color: var(--fg-muted);
    font-size: 10px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }
  .accrual .day .micro {
    color: var(--fg);
  }
  .accrual .day .cap-day {
    color: var(--gold-deep);
    font-weight: 500;
  }
  .accrual .day .carry {
    color: var(--gold-deep);
    font-size: 11px;
  }
  .accrual .day .post {
    color: var(--pine-2);
    text-align: right;
  }

  .principles {
    padding: 96px 0;
    border-bottom: 1px solid var(--rule-2);
  }
  .principles h2 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 50px;
    line-height: 1.06;
    letter-spacing: -0.008em;
    margin: 8px 0 14px;
    max-width: 22ch;
    text-wrap: pretty;
  }
  .principles h2 em {
    font-style: italic;
    color: var(--gold-deep);
  }
  .principles .lead {
    font-size: 17px;
    color: var(--fg-2);
    max-width: 56ch;
    margin: 0 0 48px;
    line-height: 1.55;
  }
  /* Cards in the principles grid are bank-ui Card primitives now;
     this rule just owns the layout. Equal-height min-height matches
     the bank-ui showcase's card-grid-3 convention. */
  .principles .grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 28px;
  }
  .principles .grid > * {
    min-height: 200px;
  }

  /* Always-dark "Run it locally" CTA section. Constant ink across
     themes — its job is to be a strong visual finish to the page. */
  .final {
    background: var(--ink);
    color: var(--bone);
    padding: 96px 0;
    position: relative;
    overflow: hidden;
  }
  .final h2 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 60px;
    line-height: 1.04;
    letter-spacing: -0.01em;
    margin: 0 0 14px;
    max-width: 18ch;
    text-wrap: pretty;
  }
  .final h2 em {
    color: var(--gold);
    font-style: italic;
  }
  .final p {
    color: rgba(244, 241, 234, 0.7);
    font-size: 17px;
    line-height: 1.55;
    max-width: 52ch;
    margin: 0 0 32px;
  }
  .final code {
    font-family: var(--mono);
    font-size: 14px;
  }
  .final .grid {
    display: grid;
    grid-template-columns: 1.05fr 1fr;
    gap: 80px;
    align-items: center;
  }
  .final .ctas {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }
  .final .btn.line {
    border-color: rgba(244, 241, 234, 0.25);
    color: var(--bone);
    background: transparent;
  }
  .final .btn.line:hover {
    background: rgba(244, 241, 234, 0.06);
  }

  footer {
    padding: 64px 0 40px;
    color: var(--fg-2);
    background: var(--surface-raised);
  }
  footer .row {
    display: grid;
    grid-template-columns: 2fr repeat(3, 1fr);
    gap: 40px;
    padding-bottom: 40px;
    border-bottom: 1px solid var(--rule);
  }
  footer h5 {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--fg-muted);
    font-weight: 500;
    margin: 0 0 16px;
  }
  footer ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
    font-size: 14px;
  }
  footer ul a:hover {
    color: var(--fg);
  }
  footer .brand-block .desc {
    margin-top: 14px;
    color: var(--fg-muted);
    font-size: 13px;
    line-height: 1.55;
    max-width: 32ch;
  }
  footer .meta-row {
    padding-top: 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
</style>
