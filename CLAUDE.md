# CLAUDE.md

Queenswood is a Clojure core-banking system, organised as a Polylith
workspace and built as a domain fork of
[`mono`](https://github.com/kjothen/mono). Bricks prefixed `bank-*`
are Queenswood-specific; everything else is shared infrastructure
inherited from upstream.

## Where to find things

- [docs/adr/](docs/adr/) — architecture decisions (the *why* behind
  every load-bearing choice). Twelve records covering mono-reuse,
  FoundationDB, the message-bus abstraction, Avro payloads, error
  handling, kebab-case keys, system-as-data, changelog watchers,
  model-equality testing, code generation, library wrapping, and
  pre-commit hooks.
- [docs/recipes/](docs/recipes/) — task-oriented recipes
  (Problem / Solution / Rules / Discussion / References). Read the
  relevant recipe before doing any non-trivial task. Twelve recipes:
  components, bases, projects, system-components,
  system-configurations, testcontainers, error-handling, testing,
  code-style, code-generation, common-helpers, git-workflow.
- [docs/design/](docs/design/) — long-form architecture deep dives
  (currently: scenario testing).
- [docs/plan/](docs/plan/) — implementation plans for in-flight work.

## Common commands

```bash
# Run all tests (default project)
clojure -M:poly test project:dev

# Run tests for one or more bricks
clojure -M:poly test brick:<brick-name> project:dev
clojure -M:poly test brick:<brick1>:<brick2> project:dev

# Code generation prep
clj -X:deps prep :aliases '[:dev]'

# Bank-specific generation, forced after a schema change
clj -X:deps prep :aliases '[:+bank :dev]' :force true

# Pre-commit hook install (once per clone)
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Critical guardrails

The rules most load-bearing across the codebase. Detail and
rationale live in the referenced recipes.

- **No throwing from `interface.clj`.** Component interfaces return
  a value or an anomaly; they never raise. Use `error/try-nom` or
  `error/try-nom-ex` to convert exceptions at library boundaries.
  See [docs/recipes/error-handling.md](docs/recipes/error-handling.md).
- **Use the `utility` brick.** `util/uuidv7` for IDs, `util/now` for
  timestamps; never `random-uuid`, `(System/currentTimeMillis)`, or
  `(Instant/now)` directly. For any helper not in `clojure.core`,
  check `utility` first. See
  [docs/recipes/code-style.md](docs/recipes/code-style.md) and
  [docs/recipes/common-helpers.md](docs/recipes/common-helpers.md).
- **No `use-fixtures` in tests.** Manage system lifecycle with
  `with-test-system`; assert anomaly-freeness with `nom-test>`. See
  [docs/recipes/testing.md](docs/recipes/testing.md).
- **Pull/merge from `main` before committing.** Renovate auto-merges
  dependency updates weekly. See
  [docs/recipes/git-workflow.md](docs/recipes/git-workflow.md).
