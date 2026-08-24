# Recipes

Four chapters, by what a recipe is about rather than by which rule
file distils it.

- **code/** — writing Queenswood: style, helpers, error handling, and
  the brick, base, project and system-wiring conventions the workspace
  is built from.
- **test/** — how tests drive the system, and the containers they run
  against.
- **practices/** — working on the repository itself: git flow, justfile
  recipes, and how these documents are written.
- **infra/** — everything that runs the bank somewhere: the cloud
  foundation, Crossplane and Argo, credentials and sign-in, the chart,
  and the recovery runbooks.

Every recipe keeps the same shape — `Problem`, `Solution`, `Rules`, an
optional `Discussion`, `References` — and carries a
`<!-- tessl-plugin: <name> -->` label naming the rule file that distils
its `## Rules`.

[CLAUDE.md](../../CLAUDE.md) routes by topic, and is where to start
from what you are trying to do rather than from where it lives.
