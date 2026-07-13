# queenswood/workflow

Development-workflow automation — how *this team* maintains its own
tooling, as opposed to `queenswood/idioms` (portable Clojure
conventions) or `queenswood/design` (how Queenswood itself is built).
The dividing line: this plugin is about the authoring process, not
about the code the process produces.

## Skills

- **[sync-rules-from-docs](skills/sync-rules-from-docs/SKILL.md)** —
  regenerates a Tessl rule file's section bodies by extracting the
  normative content straight from its linked `docs/recipes/*.md` /
  `docs/adr/*.md` sections, instead of freely paraphrasing them. Keeps
  a rule (always-loaded, agent-facing) traceable to its source (the
  hand-authored recipe/ADR) rather than drifting as an independent
  copy. Companion script: `skills/sync-rules-from-docs/extract.sh` —
  the mechanical, judgment-free extraction pass; the skill's own job is
  composing the extracted material into prose, never inventing beyond
  it.

Planned: migrating the git-workflow-shaped Claude Code skills
(`commit-and-pr`, `fresh-branch`, `check-docs`, `check-processors`,
`new-processor`, currently at `.claude/skills/`) into this plugin, so
the dev loop is Tessl-managed and evaluable the same way the Clojure
conventions are.

## Evals

Eval scenarios live in `evals/` and are **committed** — hand-authored,
same as `queenswood/idioms`'s. Two scenarios, one per source-doc shape
(recipe, ADR), each with a fixture rule section and a fixture recipe/ADR
under `inputs/`, no git history:

```bash
tessl eval run queenswood/workflow   # score with-context vs baseline
```

## Develop

Run `tessl` from this directory. `tessl plugin lint` validates the
package; `tessl skill review skills/<name>` scores a skill for quality
and flags frontmatter issues (including `allowed-tools` — declare the
minimal tool set a skill needs, space-separated, matching the
convention already used across `.claude/skills/`).
