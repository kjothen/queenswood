# Queenswood skills tile

A [Tessl](https://tessl.io) tile of skills authored in this workspace.
Each skill ships its own `SKILL.md`, `references/`, `examples/`, and
`evals/`. The plugin manifest is
[`.tessl-plugin/plugin.json`](../.tessl-plugin/plugin.json).

This directory is empty to start — skills land here as they are
authored. It is distinct from `.claude/skills/` (Claude Code's built-in
skills); this tile is the Tessl-managed, evaluable set.

## Toolchain

`tessl` is provided by the nix dev shell (`nix develop`, or automatic
via direnv). The `tessl mcp start` server is wired for Claude Code in
`.mcp.json` (per-machine, git-ignored — regenerate with `tessl init
--agent claude-code`).

## Authoring a skill

```bash
tessl login                 # once, interactive — resolves the workspace
tessl skill new <name>      # scaffold skills/<name>/ + register in the plugin
tessl scenario generate     # derive eval scenarios from the SKILL.md
tessl eval run queenswood/skills
```

See the [Tessl docs](https://docs.tessl.io) for the full authoring and
evaluation workflow.
