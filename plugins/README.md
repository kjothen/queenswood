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
| **workflow** — `queenswood/workflow` | committing, branching, PRs, the dev loop | planned |
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

```bash
mkdir -p plugins/<name> && cd plugins/<name>
tessl init --agent claude-code --name queenswood/<name>
tessl tile new --name queenswood/<name> --rules <rule> \
  --rule-description "…" --workspace queenswood   # scaffolds rules/<rule>.md
# …or `tessl skill new <skill>` for a triggered skill instead of a rule
tessl project create <name> --workspace queenswood
```

Run `tessl` commands from inside the plugin directory.
