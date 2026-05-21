<script>
  /*
    Living showcase for @queenswood/bank-ui.
    Mounts the real exported components — this page IS the spec. If it
    looks wrong, the components are wrong; if you change a component,
    refresh this page and the truth updates with it. No HTML mockups,
    no porting step.

    Sections: Marks, Wordmark, Chrome, Surfaces, Pines, Golds, Type.
  */

  import { Logo, Wordmark, AppNav, ThemeToggle, themeState, resolvedTheme } from "../src/index.js";

  const SECTIONS = [
    { id: "marks",    label: "Marks" },
    { id: "wordmark", label: "Wordmark" },
    { id: "chrome",   label: "Chrome" },
    { id: "surfaces", label: "Surfaces" },
    { id: "pines",    label: "Forest greens" },
    { id: "golds",    label: "Crown golds" },
    { id: "type",     label: "Type" },
  ];

  const VARIANTS = [
    { id: "A", note: "Eight-tree forest, gold band + teeth, baubles." },
    { id: "B", note: "Variant A plus jewels seated in the teeth." },
    { id: "C", note: "Monoline forest + crown stroke; bone-filled baubles." },
    { id: "D", note: "Four-tree forest, five-tooth crown, narrower band." },
    { id: "E", note: "Variant A inset in concentric seal rings." },
  ];

  const LOGO_SIZES = [200, 120, 80, 48, 32, 24];
  const WM_GROTESK_SIZES = [48, 32, 24, 18, 14, 12];
  const WM_SERIF_SIZES   = [72, 48, 32, 24, 18];

  // Surfaces and tokens — kept here so the showcase can display the
  // CSS expression alongside each swatch. Values mirror tokens.css;
  // if tokens.css changes the var() ref still renders correctly, but
  // the label string here is documentation and should be updated too.
  const SURFACES = [
    { name: "bone",   value: "#f4f1ea",                 onDark: false },
    { name: "bone-2", value: "#ebe6dc",                 onDark: false },
    { name: "paper",  value: "#fbf9f4",                 onDark: false },
    { name: "ink",    value: "#161310",                 onDark: true  },
    { name: "ink-2",  value: "#2a2622",                 onDark: true  },
    { name: "muted",  value: "#6b645b",                 onDark: true  },
    { name: "rule",   value: "rgba(20, 15, 10, 0.10)",  onDark: false, overlay: true },
    { name: "rule-2", value: "rgba(20, 15, 10, 0.06)",  onDark: false, overlay: true },
  ];

  const PINES = [
    { name: "pine-1", value: "oklch(0.28 0.045 150)" },
    { name: "pine-2", value: "oklch(0.36 0.055 148)" },
    { name: "pine-3", value: "oklch(0.44 0.060 145)" },
    { name: "pine-4", value: "oklch(0.52 0.060 142)" },
    { name: "pine-5", value: "oklch(0.60 0.055 140)" },
  ];

  const GOLDS = [
    { name: "gold",        value: "oklch(0.66 0.135 72)" },
    { name: "gold-bright", value: "oklch(0.78 0.140 80)" },
    { name: "gold-deep",   value: "oklch(0.52 0.120 68)" },
    { name: "gold-1",      value: "oklch(0.88 0.155 92)" },
    { name: "gold-2",      value: "oklch(0.80 0.175 86)" },
    { name: "gold-3",      value: "oklch(0.70 0.180 78)" },
    { name: "gold-4",      value: "oklch(0.56 0.160 68)" },
    { name: "gold-5",      value: "oklch(0.42 0.125 58)" },
  ];

  const DEMO_USER = {
    name: "Alex Morgan",
    "avatar-url": "data:image/svg+xml;utf8," + encodeURIComponent(
      `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'>
         <rect width='40' height='40' fill='%23ebe6dc'/>
         <circle cx='20' cy='16' r='7' fill='%236b645b'/>
         <path d='M5 40c2-9 8-13 15-13s13 4 15 13' fill='%236b645b'/>
       </svg>`
    ),
  };

  function noop() {}
</script>

<AppNav user={DEMO_USER} onSignOut={noop} />

<div class="page">
  <aside class="rail">
    <div class="rail-head">
      <span class="rail-eyebrow">bank-ui · v0.0.0</span>
      <span class="rail-title">Queenswood</span>
      <span class="rail-sub">Design system showcase</span>
    </div>
    <nav class="rail-nav">
      {#each SECTIONS as s (s.id)}
        <a href="#{s.id}">{s.label}</a>
      {/each}
    </nav>
    <p class="rail-foot">
      Real components, real tokens. Edit <code>bank-ui/src/</code> and refresh.
    </p>
  </aside>

  <main class="main">
    <header class="lede">
      <div class="eyebrow">Living spec</div>
      <h1>Queenswood, in parts.</h1>
      <p>
        Every mark, swatch, and primitive on this page is mounted from the
        real exports of <code>@queenswood/bank-ui</code>. There is no port,
        no mockup, no second source of truth.
      </p>
      <p class="lede-meta">
        Theme: pref <code>{themeState.pref}</code>, resolved <code>{resolvedTheme()}</code>.
        Cycle from the AppNav above.
      </p>
    </header>

    <!-- =================== Marks =================== -->
    <section id="marks" class="section">
      <div class="section-head">
        <span class="kicker">01 — Marks</span>
        <h2>The crowned forest, in five voices.</h2>
        <p class="lead">Five logo variants ship today. Pick by context: A for general use, B when the chrome needs a beat more decoration, C for monoline applications, D where horizontal space is tight, E for seals and stamps.</p>
      </div>

      <div class="marks-row">
        {#each VARIANTS as v (v.id)}
          <figure class="mark-card">
            <div class="mark-frame">
              <Logo variant={v.id} size={132} idPrefix="sc-{v.id}" />
            </div>
            <figcaption>
              <span class="mark-tag">Mark {v.id}</span>
              <span class="mark-note">{v.note}</span>
            </figcaption>
          </figure>
        {/each}
      </div>

      <div class="subhead">
        <h3>Size scale — Mark A</h3>
        <p>Geometry is intrinsically forgiving down to ~24 px; below that the band reads as a slab. The forest dissolves first.</p>
      </div>
      <div class="size-row">
        {#each LOGO_SIZES as size (size)}
          <div class="size-cell">
            <div class="size-frame" style:height="{size + 24}px">
              <Logo variant="A" size={size} idPrefix="sc-sz-{size}" />
            </div>
            <span class="size-label">{size}px</span>
          </div>
        {/each}
      </div>
    </section>

    <!-- =================== Wordmark =================== -->
    <section id="wordmark" class="section">
      <div class="section-head">
        <span class="kicker">02 — Wordmark</span>
        <h2>Two voices, one name.</h2>
        <p class="lead">Grotesk for product chrome, where it sits next to data and form controls. Serif for marketing, mastheads, and anywhere the name needs to slow the eye down.</p>
      </div>

      <div class="wm-grid">
        <div class="wm-col">
          <div class="wm-col-head">
            <span class="wm-name">Grotesk</span>
            <span class="wm-meta">Geist · 300/500 · 0.18em</span>
          </div>
          {#each WM_GROTESK_SIZES as s (s)}
            <div class="wm-row">
              <Wordmark variant="grotesk" size={s} />
              <span class="wm-size">{s}px</span>
            </div>
          {/each}
        </div>

        <div class="wm-col">
          <div class="wm-col-head">
            <span class="wm-name">Serif</span>
            <span class="wm-meta">Cormorant Garamond · 500 · 0.04em</span>
          </div>
          {#each WM_SERIF_SIZES as s (s)}
            <div class="wm-row">
              <Wordmark variant="serif" size={s} />
              <span class="wm-size">{s}px</span>
            </div>
          {/each}
        </div>
      </div>
    </section>

    <!-- =================== Chrome =================== -->
    <section id="chrome" class="section">
      <div class="section-head">
        <span class="kicker">03 — Chrome</span>
        <h2>AppNav &amp; theme toggle.</h2>
        <p class="lead">Sticky bar for authenticated screens. Backdrop-blurred surface, hairline rule below. The instance live at the top of this page is the same component — click the half-disc icon to cycle <code>auto → light → dark → auto</code>.</p>
      </div>

      <div class="nav-demo-stack">
        <div class="nav-demo">
          <div class="nav-demo-tag">Signed in · name + avatar</div>
          <div class="nav-demo-frame">
            <AppNav user={DEMO_USER} onSignOut={noop} />
            <div class="nav-demo-body"></div>
          </div>
        </div>

        <div class="nav-demo">
          <div class="nav-demo-tag">Signed in · name only</div>
          <div class="nav-demo-frame">
            <AppNav user={{ name: "Alex Morgan" }} onSignOut={noop} />
            <div class="nav-demo-body"></div>
          </div>
        </div>

        <div class="nav-demo">
          <div class="nav-demo-tag">Signed in · no user yet</div>
          <div class="nav-demo-frame">
            <AppNav user={null} onSignOut={noop} />
            <div class="nav-demo-body"></div>
          </div>
        </div>
      </div>

      <div class="subhead">
        <h3>ThemeToggle</h3>
        <p>The cycler in isolation. Reflects the current preference; click cycles. The resolved theme is what you see right now — if pref is <code>auto</code> and the system is dark, resolved is <code>dark</code>.</p>
      </div>
      <div class="toggle-demo">
        <ThemeToggle />
        <dl class="toggle-state">
          <div><dt>pref</dt><dd><code>{themeState.pref}</code></dd></div>
          <div><dt>resolved</dt><dd><code>{resolvedTheme()}</code></dd></div>
          <div><dt>system</dt><dd><code>{themeState.systemDark ? "dark" : "light"}</code></dd></div>
        </dl>
      </div>
    </section>

    <!-- =================== Surfaces =================== -->
    <section id="surfaces" class="section">
      <div class="section-head">
        <span class="kicker">04 — Surfaces</span>
        <h2>Paper, bone, ink.</h2>
        <p class="lead">The raw palette. Same hex values in both themes — a bone swatch is always cream. For backgrounds and text in components, prefer the semantic tokens (<code>--surface</code>, <code>--surface-raised</code>, <code>--fg</code>, <code>--fg-2</code>, <code>--fg-muted</code>), which adapt automatically.</p>
      </div>

      <div class="swatch-grid">
        {#each SURFACES as t (t.name)}
          <div class="swatch">
            <div
              class="swatch-block"
              class:overlay={t.overlay}
              style:background={t.value}
              style:color={t.onDark ? "var(--paper)" : "var(--ink)"}
            >
              <span class="swatch-aa">Aa</span>
            </div>
            <div class="swatch-meta">
              <span class="swatch-name">--{t.name}</span>
              <span class="swatch-value">{t.value}</span>
            </div>
          </div>
        {/each}
      </div>
    </section>

    <!-- =================== Pines =================== -->
    <section id="pines" class="section">
      <div class="section-head">
        <span class="kicker">05 — Forest greens</span>
        <h2>Pine, 1 through 5.</h2>
        <p class="lead">All five share chroma ~0.05 around hue 142–150. Use them as a depth scale, not as semantic states.</p>
      </div>

      <div class="ramp">
        {#each PINES as t (t.name)}
          <div class="ramp-step" style:background={t.value}>
            <span class="ramp-name">--{t.name}</span>
            <span class="ramp-value">{t.value}</span>
          </div>
        {/each}
      </div>
    </section>

    <!-- =================== Golds =================== -->
    <section id="golds" class="section">
      <div class="section-head">
        <span class="kicker">06 — Crown golds</span>
        <h2>Three crown tones, one bright ramp.</h2>
        <p class="lead"><code>gold</code>, <code>gold-bright</code>, and <code>gold-deep</code> drive the crown itself. The numbered ramp <code>gold-1</code>—<code>gold-5</code> is for accents, highlights, and the inside of borders.</p>
      </div>

      <div class="swatch-grid">
        {#each GOLDS as t (t.name)}
          <div class="swatch">
            <div class="swatch-block" style:background={t.value} style:color="var(--ink)">
              <span class="swatch-aa">Aa</span>
            </div>
            <div class="swatch-meta">
              <span class="swatch-name">--{t.name}</span>
              <span class="swatch-value">{t.value}</span>
            </div>
          </div>
        {/each}
      </div>
    </section>

    <!-- =================== Type =================== -->
    <section id="type" class="section">
      <div class="section-head">
        <span class="kicker">07 — Type</span>
        <h2>Three stacks, one feeling.</h2>
        <p class="lead">Grotesk carries product copy; serif carries the brand; mono carries the receipts.</p>
      </div>

      <article class="spec">
        <header class="spec-head">
          <span class="spec-name">Grotesk</span>
          <code class="spec-stack">--grotesk &nbsp;·&nbsp; 'Geist', ui-sans-serif, system-ui, sans-serif</code>
        </header>
        <div class="spec-display grotesk">A handsome forest, well-kept.</div>
        <p class="spec-body grotesk">
          Geist is the working face. Used for buttons, fields, dashboard chrome, body
          copy on product surfaces — anywhere the reader is here to get something done.
          Numerals are lining and tabular by default, which matters more than it should.
        </p>
        <div class="spec-scale grotesk">
          <span style="font-size:48px;font-weight:600">Aa</span>
          <span style="font-size:32px;font-weight:500">Aa</span>
          <span style="font-size:20px;font-weight:500">Aa</span>
          <span style="font-size:14px;font-weight:500">Aa</span>
          <span style="font-size:12px;font-weight:400">Aa</span>
        </div>
      </article>

      <article class="spec">
        <header class="spec-head">
          <span class="spec-name">Serif</span>
          <code class="spec-stack">--serif &nbsp;·&nbsp; 'Cormorant Garamond', ui-serif, Georgia, serif</code>
        </header>
        <div class="spec-display serif">A handsome forest, well-kept.</div>
        <p class="spec-body serif">
          Cormorant is the brand face. It sits on the landing page, in section
          headers, and anywhere the word "Queenswood" needs to slow the eye for half
          a beat. Set it generously — at small sizes its features collapse and it
          starts looking like a different font.
        </p>
        <div class="spec-scale serif">
          <span style="font-size:72px;font-weight:500">Aa</span>
          <span style="font-size:48px;font-weight:500">Aa</span>
          <span style="font-size:32px;font-weight:500">Aa</span>
          <span style="font-size:20px;font-weight:400">Aa</span>
        </div>
      </article>

      <article class="spec">
        <header class="spec-head">
          <span class="spec-name">Mono</span>
          <code class="spec-stack">--mono &nbsp;·&nbsp; 'Geist Mono', ui-monospace, monospace</code>
        </header>
        <div class="spec-display mono">A handsome forest, well-kept.</div>
        <p class="spec-body mono">
          Geist Mono carries the receipts: token names, ids, amounts in fixed
          tables, anything that wants to line up vertically. It is deliberately
          rarely used; when it appears, it means "this is data".
        </p>
        <div class="spec-scale mono">
          <span style="font-size:32px">Aa</span>
          <span style="font-size:20px">Aa</span>
          <span style="font-size:14px">Aa</span>
          <span style="font-size:12px">Aa</span>
        </div>
      </article>
    </section>

    <footer class="foot">
      <span class="foot-mark"><Logo variant="C" size={36} idPrefix="sc-foot" /></span>
      <span class="foot-text">
        End of showcase. Add new exports to <code>bank-ui/src/index.js</code> and a section here for each one.
      </span>
    </footer>
  </main>
</div>

<style>
  :global(body) {
    margin: 0;
    background: var(--surface);
    color: var(--fg);
    font-family: var(--grotesk);
    -webkit-font-smoothing: antialiased;
    text-rendering: optimizeLegibility;
  }

  .page {
    display: grid;
    grid-template-columns: 260px minmax(0, 1fr);
    gap: 0;
    max-width: 1480px;
    margin: 0 auto;
    padding: 0 32px;
  }

  /* ===== Rail ===== */
  .rail {
    position: sticky;
    top: 72px;
    align-self: start;
    height: calc(100vh - 80px);
    display: flex;
    flex-direction: column;
    gap: 28px;
    padding: 56px 24px 24px 0;
    border-right: 1px solid var(--rule-2);
  }
  .rail-head {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .rail-eyebrow {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .rail-title {
    font-family: var(--serif);
    font-size: 28px;
    font-weight: 500;
    letter-spacing: 0.01em;
    color: var(--fg);
    line-height: 1;
  }
  .rail-sub {
    font-size: 13px;
    color: var(--fg-muted);
  }
  .rail-nav {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .rail-nav a {
    display: block;
    padding: 8px 0;
    font-size: 14px;
    color: var(--fg-2);
    text-decoration: none;
    border-bottom: 1px solid var(--rule-2);
    transition: color 0.12s, padding-left 0.16s;
  }
  .rail-nav a:hover {
    color: var(--pine-4);
    padding-left: 6px;
  }
  .rail-foot {
    margin-top: auto;
    font-size: 12px;
    line-height: 1.55;
    color: var(--fg-muted);
  }
  .rail-foot code {
    font-family: var(--mono);
    font-size: 11px;
    background: var(--surface-sunk);
    padding: 1px 5px;
    border-radius: 3px;
  }

  /* ===== Main ===== */
  .main {
    padding: 56px 0 96px 48px;
    min-width: 0;
  }
  .lede {
    margin-bottom: 96px;
  }
  .eyebrow {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--gold-deep);
    margin-bottom: 18px;
  }
  .lede h1 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: clamp(48px, 6vw, 92px);
    line-height: 0.96;
    letter-spacing: -0.005em;
    margin: 0 0 24px 0;
    color: var(--fg);
  }
  .lede p {
    max-width: 56ch;
    font-size: 18px;
    line-height: 1.5;
    color: var(--fg-2);
    margin: 0;
  }
  .lede code, .section-head code, .rail-foot code, .foot-text code {
    font-family: var(--mono);
    font-size: 0.9em;
    color: var(--fg);
  }

  /* ===== Section scaffolding ===== */
  .section {
    padding: 64px 0;
    border-top: 1px solid var(--rule);
  }
  .section-head {
    margin-bottom: 48px;
  }
  .kicker {
    font-family: var(--mono);
    font-size: 12px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--fg-muted);
    display: block;
    margin-bottom: 14px;
  }
  .section-head h2 {
    font-family: var(--serif);
    font-weight: 500;
    font-size: clamp(32px, 3.4vw, 48px);
    line-height: 1.02;
    margin: 0 0 18px 0;
    color: var(--fg);
  }
  .section-head .lead {
    max-width: 64ch;
    font-size: 16px;
    line-height: 1.6;
    color: var(--fg-2);
    margin: 0;
  }
  .subhead {
    margin: 48px 0 24px 0;
  }
  .subhead h3 {
    font-family: var(--grotesk);
    font-weight: 500;
    font-size: 14px;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    margin: 0 0 8px 0;
    color: var(--fg);
  }
  .subhead p {
    font-size: 14px;
    color: var(--fg-muted);
    margin: 0;
    max-width: 60ch;
  }

  /* ===== Marks ===== */
  .marks-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 0;
    border: 1px solid var(--rule-2);
    background: var(--surface-raised);
  }
  .mark-card {
    margin: 0;
    padding: 32px 16px 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 18px;
    border-right: 1px solid var(--rule-2);
  }
  .mark-card:last-child { border-right: none; }
  .mark-frame {
    width: 132px;
    height: 132px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .mark-card figcaption {
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: center;
    text-align: center;
  }
  .mark-tag {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--gold-deep);
  }
  .mark-note {
    font-size: 12px;
    line-height: 1.45;
    color: var(--fg-muted);
    max-width: 22ch;
  }

  .size-row {
    display: flex;
    align-items: flex-end;
    gap: 32px;
    padding: 24px;
    background: var(--surface-raised);
    border: 1px solid var(--rule-2);
    overflow-x: auto;
  }
  .size-cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    flex: 0 0 auto;
  }
  .size-frame {
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .size-label {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
    letter-spacing: 0.04em;
  }

  /* ===== Wordmark ===== */
  .wm-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1px;
    background: var(--rule-2);
    border: 1px solid var(--rule-2);
  }
  .wm-col {
    background: var(--surface-raised);
    padding: 32px 28px;
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .wm-col-head {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--rule-2);
  }
  .wm-name {
    font-family: var(--mono);
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--gold-deep);
  }
  .wm-meta {
    font-size: 13px;
    color: var(--fg-muted);
  }
  .wm-row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 0;
  }
  .wm-size {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
  }

  /* ===== Chrome (AppNav demos) ===== */
  .nav-demo-stack {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .nav-demo-tag {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--fg-muted);
    margin-bottom: 10px;
  }
  .nav-demo-frame {
    border: 1px solid var(--rule);
    border-radius: 4px;
    overflow: hidden;
    background: var(--surface);
  }
  .nav-demo-body {
    height: 80px;
    background:
      repeating-linear-gradient(
        45deg,
        transparent 0,
        transparent 12px,
        var(--rule-2) 12px,
        var(--rule-2) 13px
      );
  }
  /* AppNav inside a frame: cancel the sticky so all three sit in-flow. */
  .nav-demo-frame :global(.nav) {
    position: relative;
    top: auto;
  }

  /* ===== ThemeToggle demo ===== */
  .toggle-demo {
    display: flex;
    align-items: center;
    gap: 28px;
    padding: 24px;
    background: var(--surface-raised);
    border: 1px solid var(--rule-2);
    border-radius: 4px;
    margin-top: 8px;
  }
  .toggle-state {
    margin: 0;
    display: grid;
    grid-template-columns: repeat(3, auto);
    gap: 4px 28px;
  }
  .toggle-state > div { display: flex; flex-direction: column; gap: 2px; }
  .toggle-state dt {
    font-family: var(--mono);
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--fg-muted);
    margin: 0;
  }
  .toggle-state dd {
    margin: 0;
    font-family: var(--mono);
    font-size: 13px;
    color: var(--fg);
  }
  .toggle-state code { font-family: var(--mono); }

  .lede-meta {
    margin-top: 20px !important;
    font-family: var(--mono);
    font-size: 12px !important;
    color: var(--fg-muted) !important;
    max-width: none !important;
  }
  .lede-meta code {
    background: var(--surface-sunk);
    padding: 1px 6px;
    border-radius: 3px;
    font-size: 11px;
    color: var(--fg);
  }

  /* ===== Swatches ===== */
  .swatch-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 16px;
  }
  .swatch {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .swatch-block {
    height: 140px;
    border-radius: 4px;
    display: flex;
    align-items: flex-end;
    justify-content: flex-start;
    padding: 14px;
    font-family: var(--serif);
    font-size: 48px;
    font-weight: 500;
    line-height: 1;
    border: 1px solid var(--rule-2);
  }
  .swatch-block.overlay {
    background-color: var(--surface) !important;
    background-image:
      linear-gradient(var(--rule), var(--rule));
  }
  .swatch-block.overlay > .swatch-aa { color: var(--fg); }
  .swatch-aa { display: inline-block; }
  .swatch-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .swatch-name {
    font-family: var(--mono);
    font-size: 12px;
    color: var(--fg);
    letter-spacing: 0.02em;
  }
  .swatch-value {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
  }

  /* ===== Ramps ===== */
  .ramp {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 0;
    border: 1px solid var(--rule-2);
    border-radius: 4px;
    overflow: hidden;
  }
  .ramp-step {
    aspect-ratio: 4 / 5;
    padding: 16px;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    gap: 4px;
    color: var(--paper);
  }
  .ramp-name {
    font-family: var(--mono);
    font-size: 12px;
    letter-spacing: 0.02em;
  }
  .ramp-value {
    font-family: var(--mono);
    font-size: 10px;
    opacity: 0.75;
  }

  /* ===== Type specimens ===== */
  .spec {
    padding: 32px 0;
    border-top: 1px solid var(--rule-2);
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  .spec:first-of-type { border-top: none; padding-top: 0; }
  .spec-head {
    display: flex;
    align-items: baseline;
    gap: 16px;
    flex-wrap: wrap;
  }
  .spec-name {
    font-family: var(--mono);
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--gold-deep);
  }
  .spec-stack {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--fg-muted);
  }
  .spec-display {
    font-size: clamp(40px, 5vw, 72px);
    line-height: 1;
    letter-spacing: -0.005em;
    color: var(--fg);
  }
  .spec-display.grotesk { font-family: var(--grotesk); font-weight: 500; }
  .spec-display.serif   { font-family: var(--serif);   font-weight: 500; letter-spacing: 0.01em; }
  .spec-display.mono    { font-family: var(--mono);    font-weight: 400; font-size: clamp(28px, 3.4vw, 44px); letter-spacing: 0; }
  .spec-body {
    max-width: 60ch;
    line-height: 1.55;
    color: var(--fg-2);
    margin: 0;
  }
  .spec-body.grotesk { font-family: var(--grotesk); font-size: 16px; }
  .spec-body.serif   { font-family: var(--serif);   font-size: 22px; line-height: 1.45; }
  .spec-body.mono    { font-family: var(--mono);    font-size: 13px; }
  .spec-scale {
    display: flex;
    align-items: baseline;
    gap: 24px;
    padding: 8px 0;
    color: var(--fg);
  }
  .spec-scale.grotesk { font-family: var(--grotesk); }
  .spec-scale.serif   { font-family: var(--serif); }
  .spec-scale.mono    { font-family: var(--mono); }

  /* ===== Foot ===== */
  .foot {
    margin-top: 96px;
    padding: 32px 0;
    border-top: 1px solid var(--rule);
    display: flex;
    align-items: center;
    gap: 20px;
  }
  .foot-text {
    font-size: 13px;
    color: var(--fg-muted);
    max-width: 60ch;
  }
</style>
