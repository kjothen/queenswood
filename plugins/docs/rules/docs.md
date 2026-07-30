# Queenswood docs conventions

How to write or edit anything under `docs/` — formatting, link
hygiene, tone, and the PRD-vs-TDD register split. Deterministic where
possible (wrap width, link shape); a discipline, not a gate, where it
isn't (tone, register).

## Hard-wrap and link hygiene

Hard-wrap markdown at 80 columns under `docs/`. Use the canonical
reference-list link pattern — `[ID](path) — Title`, link intact,
title as trailing prose that may wrap — and only single-level
relative links (`../adr/...`); never climb `../../`, never put `)`
immediately after a link's closing paren, never wrap link text
across lines, never use inline code as an entire link's text. Inside
mermaid labels, notes, and arrow text, replace `;` with `,`, `—`,
`.`, or `<br/>` — mermaid treats `;` as a statement separator and
GitHub fails to render the block. A mermaid line MAY exceed 80
characters when the diagram reads more clearly as one line.
See [writing-docs](../../../docs/recipes/writing-docs.md).

## Tone, maturity claims, and the PRD register

Don't describe `mono`, Queenswood, or their components as
"battle-tested", "production-proven", or similar maturity claims —
neither project has production miles yet. Don't name specific
competitor banks or fintechs in any doc; cite a public spec, RFC, or
standard instead when a reference is useful. Don't pin a doc to a
specific count of repo artefacts (ADRs, recipes, bricks) or to a
relative-time framing ("recently", "as of…") — both age worse than
the prose around them. In a PRD, use non-technical product language
— never engineering vocabulary (sync/async, reactive, relay,
handler, primitive) — and never name a specific operation
(`create-organization`); describe what a user does via "the banking
API," not the call it makes. Frame a code-quality rule as a
principle and discipline rather than a mechanical CI gate, and
acknowledge drift in a recipe's Harder consequences; reserve the
project's own vocabulary (`changelog relay`, `brick`,
`interceptor`) for TDDs and recipes.
See [writing-docs](../../../docs/recipes/writing-docs.md).
