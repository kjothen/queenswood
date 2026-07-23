# Plan: consolidate processor services along the financial boundary

## Context

Ten processor microservices each run one command processor. At current
volume they are under-utilised: ten JVMs, ten deployments, ten CI
project runs, each paying the FDB Record Layer and Pulsar client
footprint to consume a trickle of commands. The composition of a
service is already entirely YAML-driven — every processor base is the
same boilerplate `main.clj` plus a require bundle, and the monolith
proves all processors coexist in one JVM — so consolidation is a
packaging change, not an architecture change. The bus topology,
topics, subscriptions, and per-domain bricks are untouched.

Grouping is by boundary, not performance: financial operations
(posting, settling, accruing, payment gating) never share a JVM with
operational ones (provisioning, identity), external adapters and
simulators stay in their own services, and the scheduler stays alone
because its Quartz timer is a per-JVM singleton that must never be
replicated.

Target shape — 10 processor deployments become 3:

| Service | Hosts | Boundary |
|---------|-------|----------|
| `financial-processors-service` (new) | payment, transaction, interest, payee-check | money path: posts/settles/accrues or gates a payment |
| `operational-processors-service` (new) | bank, party, cash-account, cash-account-product, idv | provisioning + identity: creates and configures the records money moves through |
| `scheduler-processor-service` (unchanged) | Quartz runner | singleton timer infra; fires commands consumed by the financial group |

Adapters (`clearbank-adapter`, `onfido-adapter`) and the
three simulators are out of scope and keep their own deployments.

## Design decisions

1. **One generic base, two projects.** New `bases/processors`:
   a `main.clj` identical to today's processor mains (CLI name
   `processors`) and a `system.clj` require bundle that is the
   union of the nine absorbed bundles (all processor brick
   interfaces + avro, command-processor, event-processor, fdb,
   http-client, identity-provider, keycloak, message-bus, pulsar,
   telemetry). Registration of a component-kind a given YAML never
   instantiates is harmless, so both services share the base; which
   processors a service runs is decided by its `application.yml`
   alone. Regrouping later is a YAML move, no code.
2. **Cursor continuity.** Every Pulsar `subscriptionName` (e.g.
   `payment-service-payments-command`) and every FDB watcher
   `consumer-id` (e.g. `parties-watcher`) is carried over verbatim
   from the absorbed service, so the new pods resume the existing
   subscriptions and changelog cursors with no reprocessing window
   beyond the ordinary at-least-once overlap of a rolling deploy.
3. **Replicas stay 1.** Watchers are single-cursor consumers
   (ADR-0008's known limitation) and both groups host watchers.
   Command consumption via Shared subscriptions could scale with
   replicas, but not until watchers get leader election — same
   constraint as the per-processor services have today, now written
   down.
4. **Names.** `financial` / `operational` (adjectival) rather than
   `transaction-processors`, which would be one character away from
   the deleted `transaction-processor-service` in every grep,
   Helm values file, and dashboard.

## Changes

### New base and projects

- `bases/processors/` — `deps.edn`, `main.clj`, `system.clj`
  (superset bundle as above). Mirror `bases/party-processor`.
- `projects/financial-processors-service/` — `deps.edn` is the
  union of the four absorbed projects' deps plus the base;
  `resources/application.yml` merges their pulsar sections verbatim
  (payments-command(+response), schemes-payments-event and the
  schemes command topics, transactions-command(+response),
  interest-command(+response), payee-checks-command(+response), all
  DLQs, subscription names unchanged) and includes
  `bank/payment.yml`, `bank/transaction.yml`, `bank/interest.yml`,
  `bank/payee-check.yml` copied unmodified from the absorbed
  projects. `logback*.xml` copied. Payee-check keeps its
  `CLEARBANK_ADAPTER_URL` env (moves to this service's Helm
  `extraEnv`).
- `projects/operational-processors-service/` — same mechanics:
  banks-command, parties-command, cash-accounts-command,
  cash-account-products-command, idv command/event topics;
  includes `bank/bank.yml` (with its `keycloak.identity-provider`
  component block and `KEYCLOAK_*` env), `bank/party.yml` and
  `bank/cash-account.yml` (their watchers ride along),
  `bank/cash-account-product.yml`, `bank/idv.yml`.

### Deletions

Nine bases and nine projects: `{payment, transaction, interest,
payee-check, bank, party, cash-account, cash-account-product,
idv}-processor` bases and their `-service` projects.
`scheduler-processor` (base + project) is untouched.

### Workspace plumbing

- `workspace.edn` — remove the nine project entries; add the two new
  ones (aliases e.g. `fin`, `ops`; same `:necessary` list).
- `Tiltfile` — `SERVICES` / `PROCESSORS`: nine out, two in.
- `infra/helm/queenswood/values.yaml` — nine service entries out,
  two in (`replicas: 1`; resources sized between a single processor
  and the monolith — start at `limits 1500m/2Gi, requests
  300m/1Gi` for financial, slightly lower for operational, tune in
  kind). `values-monolith.yaml` — replace the nine `enabled: false`
  lines with two.
- `monolith-service` is unaffected (it composes the same
  processor components in-process via its own YAML).

### Docs and skills

- `docs/adr/0019-processor-packaging.md` — processor packaging is
  deployment-time composition: the paired `X` brick remains the
  unit of code; which JVM hosts it is YAML. Records the boundary
  rule (financial / operational / adapters / singleton scheduler),
  the cursor-continuity requirement when regrouping, and the
  replicas constraint. Cross-reference ADR-0018.
- `docs/tdd/processor-bricks.md` — amend the "paired
  `X-processor` base" convention: the pair is now brick +
  *hosting entry* in a combined processors service; a new processor
  adds its `bank/X.yml` and pulsar wiring to the right group's
  project instead of scaffolding a base.
- `.claude/skills/new-processor/SKILL.md` — same amendment to the
  scaffold workflow (steps 3–5 become "wire into the group project").
- `docs/recipes/projects.md` / `deployment.md` — update the service
  inventory if they enumerate it.

### Test systems

Unchanged. Brick tests and both scenario suites compose their own
test YAMLs (`bank-test-resources`, `bank-test-api-scenarios`) and
never reference service projects. CI's per-project matrix shrinks by
nine.

## Sequencing

1. Base + financial project + workspace/Tilt/Helm for it; delete its
   four absorbed services. `poly check`.
2. Operational project; delete its five absorbed services.
   `poly check`.
3. Docs + skill amendments, ADR-0019.
4. Full verification.

(1 and 2 can be one commit if review size allows; docs separate.)

## Verification

1. `clojure -M:poly check` — 0 errors (expect the deps unions to
   need one or two iterations, as with processor-service).
2. `clojure -M:poly test project:dev :all` — full matrix green
   (test systems unaffected, so this guards against accidental brick
   damage only).
3. `clojure -M:poly test :all` — the per-service matrix now runs the
   two new projects' classpaths.
4. Tilt e2e in kind: `tilt up`, confirm the two processor pods come
   up, then drive create-bank → create-party → open account →
   internal payment through the API and watch both pods log their
   respective commands (financial pod: payment/transaction;
   operational pod: bank/party/cash-account).
5. `/bank-guardrails` + `/check-docs` on the branch.
