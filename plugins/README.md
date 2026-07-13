# Tessl plugins

Queenswood's [Tessl](https://tessl.io) plugins. Each subdirectory is a
**self-contained plugin** — its own `.tessl-plugin/plugin.json`,
`tessl.json` (project link), `skills/`, and `evals/` — installable on
its own (`tessl install file:./plugins/<name>`).

They are split by **task-trigger**, not tidiness: a plugin is the unit
that gets loaded into an agent's context, so the goal is that any task
pulls only the one or two plugins it needs, and nothing else. The axis
is *portability* — code hygiene that would apply to any Clojure repo,
versus the design that is specific to how Queenswood is built.

## The map

| Plugin | Reach for it when… | Status |
|--------|--------------------|--------|
| **[idioms](idioms/)** — `queenswood/idioms` | writing any Queenswood Clojure: anomalies-not-exceptions, `utility` helpers, kebab-case keys, brick-boundary imports, style | **live** (rule: `idioms`) |
| **design** — `queenswood/design` | how the system is built, brick to topology: Polylith bricks, `interface.clj` discipline, the processor pattern, CQRS split, changelog-as-outbox, transaction boundaries, system-as-data | planned |
| **[workflow](workflow/)** — `queenswood/workflow` | committing, branching, PRs, the dev loop, keeping Tessl rules in sync with their source docs | **live** (skill: `sync-rules-from-docs`) |
| **docs** — `queenswood/docs` | writing or checking docs (wrap-80, mermaid, tone, PRD register) | planned |
| **security** — `queenswood/security` | secrets, auth, SAST, security review | planned |
| **deployment** — `queenswood/deployment` | deploying / running the cluster (Helm, Tilt, kind, Crossplane) | planned |

Decision rule when a new skill or rule wants a home: *would it help on
any Clojure repo → `idioms`; is it how Queenswood specifically is built
→ `design`.* Keep `design` whole (low-level brick shape and high-level
topology are one body of knowledge); split it only if it ever bloats
context.

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
