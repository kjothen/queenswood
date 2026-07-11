# Diagrams

Excalidraw sources (`*.excalidraw`) plus their exported SVGs. The SVGs
are theme-split: a `-light` and a `-dark` variant per diagram, both with
a transparent background, so they read cleanly on GitHub in either
colour scheme via a `<picture>` block.

## System diagram

<picture>
  <source media="(prefers-color-scheme: dark)"  srcset="system-diagram-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="system-diagram-light.svg">
  <img alt="Queenswood system diagram" src="system-diagram-light.svg">
</picture>

Colour split: grey external actors, blue ingress and message bus, violet
write side (processors), green read side (queriers), amber record store,
orange egress adapters.

## Regenerating the SVGs

The exports are produced headlessly from the `.excalidraw` sources by the
tooling in [`tooling/`](tooling), which drives Excalidraw's own
`exportToSvg` so fonts and hand-drawn strokes match the editor exactly.
From the workspace root:

```bash
just diagram                       # the system diagram (default)
just diagram Idempotency.excalidraw   # a single named scene
just diagrams                      # every docs/diagrams/*.excalidraw
```

The first run installs the tooling's npm deps and a headless Chromium.
Output lands next to the sources as `<slug>-light.svg` and
`<slug>-dark.svg`; re-run after editing a scene and commit the refreshed
SVGs alongside it. To run the exporter directly instead of via `just`,
see [`tooling/`](tooling).
