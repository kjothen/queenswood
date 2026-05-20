<script>
  /* Queenswood logo marks — ported verbatim from
     design_handoff_queenswood_logo/logo-marks.jsx.
     The numeric SVG geometry IS the source of truth; do not
     eyeball changes — adjust the source then port. */

  let { variant = "A", size = 120, idPrefix = "qw" } = $props();

  // Three forest sets. Each entry: {cx, h, bw}.
  // Bases overlap the spacing so the forest reads as layered.
  const SETS = {
    A: [
      { cx: 22,  h: 48,  bw: 40 },
      { cx: 50,  h: 78,  bw: 46 },
      { cx: 78,  h: 64,  bw: 40 },
      { cx: 106, h: 104, bw: 50 },
      { cx: 134, h: 84,  bw: 44 },
      { cx: 162, h: 116, bw: 52 },
      { cx: 190, h: 76,  bw: 44 },
      { cx: 216, h: 56,  bw: 38 },
    ],
    B: [
      { cx: 24,  h: 52,  bw: 38 },
      { cx: 50,  h: 82,  bw: 44 },
      { cx: 76,  h: 70,  bw: 40 },
      { cx: 102, h: 108, bw: 48 },
      { cx: 130, h: 92,  bw: 44 },
      { cx: 158, h: 120, bw: 52 },
      { cx: 188, h: 86,  bw: 46 },
      { cx: 216, h: 60,  bw: 40 },
    ],
    C: [
      { cx: 44,  h: 68,  bw: 44 },
      { cx: 86,  h: 104, bw: 54 },
      { cx: 134, h: 84,  bw: 48 },
      { cx: 180, h: 60,  bw: 42 },
    ],
  };

  // Variant defaults — Mark D narrows the crown band and uses 5 teeth.
  const VARIANT_OPTS = {
    A: { forestSet: "A", monolineForest: false, teeth: 7, x1: 36, x2: 204, bandH: 16, toothH: 46, jewels: false, monolineCrown: false, sealed: false },
    B: { forestSet: "B", monolineForest: false, teeth: 7, x1: 36, x2: 204, bandH: 16, toothH: 46, jewels: true,  monolineCrown: false, sealed: false },
    C: { forestSet: "A", monolineForest: true,  teeth: 7, x1: 36, x2: 204, bandH: 16, toothH: 46, jewels: false, monolineCrown: true,  sealed: false },
    D: { forestSet: "C", monolineForest: false, teeth: 5, x1: 48, x2: 192, bandH: 18, toothH: 42, jewels: false, monolineCrown: false, sealed: false },
    E: { forestSet: "A", monolineForest: false, teeth: 7, x1: 36, x2: 204, bandH: 16, toothH: 46, jewels: false, monolineCrown: false, sealed: true  },
  };

  let opts = $derived(VARIANT_OPTS[variant] ?? VARIANT_OPTS.A);
  let trees = $derived(SETS[opts.forestSet]);
  // Render shortest first so taller trees paint on top.
  let renderOrder = $derived([...trees.keys()].sort((a, b) => trees[a].h - trees[b].h));

  const baseY = 120;
  const baubleR = 3.6;

  // Per-tree gradient — hue 138-156, lightness varies so the forest has
  // depth instead of looking like one flat shape.
  function gradientFor(i) {
    const hue  = 138 + ((i * 7) % 18);
    const topL = 0.66 + ((i * 5) % 10) / 100;
    const botL = 0.20 + ((i * 3) % 8) / 100;
    return {
      top: `oklch(${topL.toFixed(3)} 0.045 ${hue})`,
      bot: `oklch(${botL.toFixed(3)} 0.055 ${hue})`,
    };
  }

  function trianglePoints(t) {
    return `${t.cx},${baseY - t.h} ${t.cx - t.bw / 2},${baseY} ${t.cx + t.bw / 2},${baseY}`;
  }

  // Crown polygon construction — solid band hanging from baseY plus
  // downward-pointing teeth. Apexes carry the bauble centers.
  let crown = $derived.by(() => {
    const { x1, x2, teeth, bandH, toothH } = opts;
    const w = x2 - x1;
    const step = w / teeth;
    const points = [];
    const apexes = [];
    points.push(`${x1},${baseY}`);
    points.push(`${x2},${baseY}`);
    points.push(`${x2},${baseY + bandH}`);
    for (let i = 0; i < teeth; i++) {
      const segL = x2 - i * step;
      const segR = x2 - (i + 1) * step;
      const apexX = (segL + segR) / 2;
      apexes.unshift(apexX);
      points.push(`${apexX},${baseY + bandH + toothH}`);
      if (i < teeth - 1) points.push(`${segR},${baseY + bandH}`);
    }
    points.push(`${x1},${baseY + bandH}`);
    return { path: points.join(" "), apexes };
  });

  const goldFill  = "oklch(0.66 0.135 72)";
  const goldDeep  = "oklch(0.58 0.13 72)";
  const goldDark  = "oklch(0.52 0.12 68)";
  const goldLight = "oklch(0.82 0.13 82)";

  let baubleY = $derived(baseY + opts.bandH + opts.toothH + baubleR + 0.6);

  let pineMid = "oklch(0.44 0.060 145)"; // PINE[2] — monoline forest stroke
  let pineFour = "oklch(0.52 0.060 142)"; // PINE[3] — monoline horizon line
</script>

<svg
  viewBox="0 0 240 240"
  width={size}
  height={size}
  aria-label="Queenswood symbol"
>
  {#if opts.sealed}
    <circle cx="120" cy="120" r="106" fill="none" stroke="currentColor" stroke-opacity="0.35" stroke-width="1" />
    <circle cx="120" cy="120" r="100" fill="none" stroke="currentColor" stroke-opacity="0.13" stroke-width="0.5" />
    <g transform="scale(0.84) translate(23, 23)">
      <!-- inner mark for Mark E -->
      <defs>
        {#each trees as _t, i}
          {@const g = gradientFor(i)}
          <linearGradient id="{idPrefix}-e-tg-{i}" x1="0" y1="0" x2="0" y2="1" gradientUnits="objectBoundingBox">
            <stop offset="0%" stop-color={g.top} />
            <stop offset="100%" stop-color={g.bot} />
          </linearGradient>
        {/each}
      </defs>
      {#each renderOrder as i (i)}
        <polygon points={trianglePoints(trees[i])} fill="url(#{idPrefix}-e-tg-{i})" />
      {/each}
      <line x1="14" y1={baseY} x2="226" y2={baseY} stroke="currentColor" stroke-opacity="0.18" stroke-width="0.75" />
      <polygon points={crown.path} fill={goldFill} />
      <line x1={opts.x1 + 4} y1={baseY + opts.bandH - 1.2} x2={opts.x2 - 4} y2={baseY + opts.bandH - 1.2} stroke={goldDark} stroke-opacity="0.6" stroke-width="0.8" />
      {#each crown.apexes as ax, i (i)}
        <g>
          <circle cx={ax} cy={baubleY} r={baubleR} fill={goldFill} />
          <circle cx={ax - baubleR * 0.35} cy={baubleY - baubleR * 0.35} r={baubleR * 0.28} fill={goldLight} opacity="0.85" />
        </g>
      {/each}
    </g>
  {:else}
    <defs>
      {#each trees as _t, i}
        {@const g = gradientFor(i)}
        <linearGradient id="{idPrefix}-tg-{i}" x1="0" y1="0" x2="0" y2="1" gradientUnits="objectBoundingBox">
          <stop offset="0%" stop-color={g.top} />
          <stop offset="100%" stop-color={g.bot} />
        </linearGradient>
      {/each}
    </defs>

    {#each renderOrder as i (i)}
      {#if opts.monolineForest}
        <polygon points={trianglePoints(trees[i])} fill="none" stroke={pineMid} stroke-width="2" stroke-linejoin="round" />
      {:else}
        <polygon points={trianglePoints(trees[i])} fill="url(#{idPrefix}-tg-{i})" />
      {/if}
    {/each}

    {#if opts.monolineCrown}
      <line x1="14" y1={baseY} x2="226" y2={baseY} stroke={pineFour} stroke-width="2" stroke-linecap="round" />
    {:else}
      <line x1="14" y1={baseY} x2="226" y2={baseY} stroke="currentColor" stroke-opacity="0.16" stroke-width="0.75" />
    {/if}

    {#if opts.monolineCrown}
      <polygon points={crown.path} fill="none" stroke={goldDeep} stroke-width="2.5" stroke-linejoin="round" />
    {:else}
      <polygon points={crown.path} fill={goldFill} />
      <line x1={opts.x1 + 4} y1={baseY + opts.bandH - 1.2} x2={opts.x2 - 4} y2={baseY + opts.bandH - 1.2} stroke={goldDark} stroke-opacity="0.6" stroke-width="0.8" />
    {/if}

    {#each crown.apexes as ax, i (i)}
      {#if opts.monolineCrown}
        <circle cx={ax} cy={baubleY} r={baubleR} fill="var(--bone)" stroke={goldDeep} stroke-width="2.5" />
      {:else}
        <g>
          <circle cx={ax} cy={baubleY} r={baubleR} fill={goldFill} />
          <circle cx={ax - baubleR * 0.35} cy={baubleY - baubleR * 0.35} r={baubleR * 0.28} fill={goldLight} opacity="0.85" />
        </g>
      {/if}
    {/each}

    {#if opts.jewels && !opts.monolineCrown}
      {#each crown.apexes as ax, i (i)}
        <circle cx={ax} cy={baseY + opts.bandH + opts.toothH - 8} r="2.2" fill={goldLight} />
      {/each}
    {/if}
  {/if}
</svg>
