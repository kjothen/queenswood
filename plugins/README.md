# Tessl plugins

Queenswood's [Tessl](https://tessl.io) plugins. Each subdirectory is a
**self-contained plugin** — its own `.tessl-plugin/plugin.json`,
`tessl.json` (project link), `skills/`, and `evals/` — installable on
its own (`tessl install file:./plugins/<name>`).

They are split by **task-trigger**, not tidiness: a plugin is the unit
that gets loaded into an agent's context, so the goal is that any task
pulls only the one or two plugins it needs, and nothing else. Three
axes: *portability* — code hygiene that would apply to any Clojure
repo; the *framework* — Polylith mechanics, independent of how
Queenswood specifically uses them; and the *design* that's specific to
how Queenswood is built on top of that framework.

## The map

| Plugin | Reach for it when… | Status |
|--------|--------------------|--------|
| **[idioms](idioms/)** — `queenswood/idioms` | writing any Queenswood Clojure: anomalies-not-exceptions, `utility` helpers, kebab-case keys, style | **live** (rule: `idioms`) |
| **[framework](framework/)** — `queenswood/framework` | using Polylith itself: bases, components, projects, `interface.clj` discipline, one-brick-per-library | **live** (rule: `framework`) |
| **[design](design/)** — `queenswood/design` | how the system is built, brick to topology: the processor pattern, CQRS split, changelog-as-outbox, transaction boundaries, system-as-data | **live** (rule: `design`) |
| **[workflow](workflow/)** — `queenswood/workflow` | committing, branching, PRs, the dev loop, keeping Tessl rules in sync with their source docs | **live** (skill: `sync-rules-from-docs`, rule: `workflow`) |
| **[docs](docs/)** — `queenswood/docs` | writing or checking docs (wrap-80, mermaid, tone, PRD register) | **live** (rule: `docs`) |
| **security** — `queenswood/security` | secrets, auth, SAST, security review | planned |
| **[deployment](deployment/)** — `queenswood/deployment` | deploying / running the cluster (Helm, Tilt, kind, Crossplane) | **live** (rule: `deployment`) |

Decision rule when a new skill or rule wants a home: *would it help on
any Clojure repo → `idioms`; is it Polylith-the-tool, not
Queenswood-specific → `framework`; is it how Queenswood specifically is
built on top of Polylith → `design`.* Keep `design` whole (low-level
system wiring and high-level topology are one body of knowledge); split
it only if it ever bloats context.

## Toolchain

`tessl` is provided by the nix dev shell (`nix develop`, or automatic
via direnv). `.mcp.json` at the repo root wires the `tessl mcp start`
server for Claude Code (workspace-level; one server serves every
plugin). These plugins are distinct from `.claude/skills/` — Claude
Code's built-in skills — this is the Tessl-managed, evaluable set.

## Adding a plugin

`tessl init` re-syncs the *parent* project when run from a directory
already nested inside an initialized Tessl tree (which every
`plugins/<name>` is, once `plugins/idioms` exists) — it won't create a
fresh nested project. Skip it and scaffold directly with `tessl tile
new`, then hand-write `tessl.json` to match a sibling plugin's shape:

```bash
mkdir -p plugins/<name> && cd plugins/<name>
tessl tile new --name queenswood/<name> --path . --workspace queenswood \
  --rules <rule> --rule-description "…"        # scaffolds rules/<rule>.md
# …or --skill --skill-name <skill> --skill-description "…" for a
# triggered skill instead of a rule

cat > tessl.json <<'EOF'
{ "name": "queenswood/<name>", "mode": "vendored", "dependencies": {} }
EOF
```

Then register the new plugin as a dependency in the repo-root
`tessl.json` (alongside the existing entries), and run `tessl install`
from the repo root to link it in.

Run `tessl` commands from inside the plugin directory.
