# queenswood/idioms

How to write **idiomatic Queenswood Clojure** — the portable code
conventions that apply to any namespace in the workspace, as opposed to
the workspace's distinctive system design (that's `queenswood/design`).
The dividing line: an idiom would still be good advice on another
Clojure repo; a design rule is meaningless outside this system.

## Rules

- **[idioms](rules/idioms.md)** — the always-loaded conventions for
  writing Queenswood Clojure: return anomalies instead of throwing
  across a boundary, take IDs and timestamps from `utility`, drive
  tests with `with-test-system`, cross a brick boundary only through
  `interface.clj`, and comment the *why* not the *what*.

Rules are guidance — they shape how code gets written. Enforcement is
separate and deterministic: the `no-raw-throw` semgrep rule
(`.semgrep.yml`) runs in the pre-commit hook, so a regression is caught
by the linter, not by asking an agent to look.

Planned: dedicated code-style rules (requires/naming/`cond->`) and a
kebab-case-keys rule, split out if this file ever grows unwieldy.

## Evals

Eval scenarios live in `evals/` and are **not committed** — one fixture
carries its own `.git` (a branch-diff scenario), which can't nest
inside this repo, and Tessl's fixture upload respects `.gitignore`, so
ignoring them would break `eval run`. They stay untracked-but-visible.
The recorded scenarios were authored for the earlier detection skill;
regenerate them against the rules before scoring:

```bash
tessl scenario generate . --count 5 --workspace queenswood   # new coverage
tessl eval run queenswood/idioms                             # score vs baseline
```

## Develop

Run `tessl` from this directory. `tessl plugin lint` validates the
package; `tessl review run skills/<name>` scores a skill for quality.
