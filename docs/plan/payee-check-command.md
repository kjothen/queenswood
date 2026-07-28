# Plan: CoP check via command request-reply

Convert the Confirmation of Payee (CoP) check from a direct HTTP call made
inside `api` into a command request-reply handled by a processor, so the
Web Service API never invokes an external adapter directly. This realises the
"Replace Invoke with Command Request/Reply" TODO on the system diagram.

## Target flow

```
api (create-check)
   |
   +- commands/send -> "check-payee" command  --> Message Bus
                                                      |
                                   payee-check PROCESSOR (new)
                                     |- core: invoke CoP adapter over HTTP
                                     |- store/save-check -> FDB
                                     +- reply: saved check  --> Message Bus
api <-- Command Reply (ACCEPTED + payload) -------+   -> 201
```

`payee-check` is promoted from a plain persistence component into a
processor brick, modelled on `idv` / `cash-account`. The read path
(`queries.clj` -> `get-check` / `get-checks`) is untouched; that is the
diagram's Query-Handlers edge and stays direct-to-store.

The work splits into Part A (the architecture change; keeps the workspace and
tests green in the monolith) and Part B (the standalone deployable service, to
match `idv` having its own base and project). Part A is the tight PR;
Part B can follow.

## Part A: the core change

### 1. Avro command and reply schemas

The `schemas` serde map is explicitly enumerated (`avro-test.yml`), not
auto-discovered, and `schemas/payee-check/` today holds only
`payee_check.proto` (no Avro yet).

- Add `components/schema/resources/schemas/payee-check/check-payee.avsc.json`
  - command payload: `bank-id`, `creditor-name`,
    `account {sort-code, account-number}`, `account-type`.
- Add `.../payee-check/payee-check.avsc.json`
  - reply payload: the saved check shape (`check-id`, `bank-id`, `request`,
    `result {match-result, actual-name, reason-code, reason}`, `created-at`,
    `expires-at`).
- Register both in `avro-test.yml` and the prod
  `resources/.../avro.yml`:

  ```yaml
  check-payee: schemas/payee-check/check-payee.avsc.json
  payee-check: schemas/payee-check/payee-check.avsc.json
  ```

### 2. Promote `components/payee-check` to a processor brick

Add the two files that make it a processor (mirroring `idv`):

- `commands.clj` - `dispatch` plus `defrecord PayeeCheckProcessor [config]`
  implementing `processor/Processor`. One handler, `"check-payee"`, which
  serialises the saved check with the `"payee-check"` schema to
  `{:status "ACCEPTED" :payload ...}`.
- `system.clj` - `system/defcomponents :payee-check` exposing `:processor`,
  with `:system/config` requiring `record-db`, `record-store`, `schemas`, plus
  `clearbank-adapter-url` (the URL moves here from the server).

Change `core.clj` - add `check-and-save`, which threads invoke adapter ->
persist. Move `perform-cop-check` out of `api` to here (or a small
`adapter.clj`; `idv` keeps its external effect in `core`, so `core` is
consistent). It requires `http-client` and `json`.

Preserve the soft-failure semantics: today an adapter failure does not error
the request - it returns `{:match-result :match-result-unavailable, :reason-code
"ACNS"}`, which still gets persisted and returned 201. Keep that: a CoP invoke
failure must not become a REJECTED or FAILED command; it maps to the
unavailable result, still persists, still replies ACCEPTED.

`domain.clj` and `store.clj` are unchanged (`save-check` already accepts
`txn-or-config` via `fdb/transact`).

### 3. `api`: replace the direct call

- `payee_check/handlers.clj` - delete `perform-cop-check`; drop the
  `http-client` and `json` requires. `create-check` becomes a `commands/send`:

  ```clojure
  (commands/send (dispatcher request) request "check-payee" "payee-check"
                 (assoc body :bank-id bank-id))
  ```

  plus a private `dispatcher` helper reading `(-> request :dispatchers
  :payee-checks)` (copy from `cash_account/commands.clj`).
- Preserve 201: `commands/send` hardcodes `{:status 200}` on success. The POST
  is documented 201, so remap (wrap the result and bump ACCEPTED -> 201, or add
  an optional success-status arg to `commands/send`).
- `queries.clj`, `routes.clj`, `coercion.clj`, `links.clj` unchanged.

### 4. Monolith and scenario system wiring

- New `components/test-resources/test-resources/system/payee-check-test.yml`
  mirroring `cash-account-test.yml`: `processor` (kind `payee-check/processor`,
  with `clearbank-adapter-url: !system/ref clearbank-adapter-server.http-url`),
  `command-processor` (channels `payee-checks-command` /
  `payee-checks-command-response`), `dispatcher`.
- Add `payee-checks: !include system/payee-check-test.yml` to the two
  `application-test.yml` files that have a server + dispatchers: bank-monolith
  and bank-test-api-scenarios. (test-scenarios is domain-layer — it only
  reads via `get-check`, so it needs no processor wiring.)
- In the server interceptors block of those two files: add `payee-checks:
  !system/ref payee-checks.dispatcher` to `dispatchers`, and remove
  `clearbank-adapter-url` from the server (only payee-check used it).
- Pulsar channel plumbing: add `payee-checks-command` and
  `payee-checks-command-response` to all five sections (topics, producers,
  consumers, message-bus-producers, message-bus-consumers) of both
  `pulsar-test.yml` files. Each command channel needs this; it was missed in
  the first cut and is why the dispatcher failed to start.

### 4a. Server-assigned command id

The payee-check POST has no `require-idempotency-key` interceptor (a CoP check
is non-idempotent and the API takes no client key), so the command envelope's
`id` / `correlation-id` are nil. `api.commands/send` now assigns a
generated id when none is present — required because Avro rejects a null `id`
and, more importantly, the dispatcher correlates replies by `correlation-id`,
so a nil would collide across concurrent requests. Behaviour-preserving for
idempotency-keyed routes (their id is always set).

### 5. Tests

- The HTTP contract is unchanged, so existing test-api-scenarios CoP
  scenarios should still pass, now exercising the bus round-trip.
- Add a brick-level processor test for `payee-check` (mirror
  `idv/interface_test.clj`): match result persisted and replied;
  adapter-down -> `match-result-unavailable` still persisted and ACCEPTED.
- `clearbank-adapter-server` and `clearbank-simulator-server` are already in
  those systems, so the moved invoke has its target.

## Part B: standalone deployable plus prod wiring (as implemented)

The dedicated processor service and all production wiring (Part A only wired
the monolith and scenario test systems). Modelled on the synchronous
`cash-account-processor-service`, not the async `idv` one.

- `bases/payee-check-processor/` - `deps.edn`, `main.clj`, and a
  `system.clj` bare-require bundle that registers every component-kind the
  processor needs.
- `projects/payee-check-processor-service/` - `deps.edn` (mirrors the
  cash-account processor service plus `component/json`, which `core.clj`
  uses), `resources/application.yml` (consumes `payee-checks-command`,
  produces `payee-checks-command-response`), `resources/bank/payee-check.yml`
  (processor + command-processor, `clearbank-adapter-url: !env
  CLEARBANK_ADAPTER_URL`), and the two `logback` files.
- `workspace.edn` - register the project (alias `pyc`).
- `projects/api-service/resources/application.yml` - add the
  `payee-checks-command` producer, `payee-checks-command-response` consumer,
  their message-bus entries, the `payee-checks` dispatcher, and
  `payee-checks: !system/ref payee-checks.dispatcher` under server dispatchers;
  drop the now-unused `clearbank-adapter-url: !env CLEARBANK_ADAPTER_URL`.
- Deploy manifests: `infra/helm/queenswood/values.yaml` (new service entry
  carrying `CLEARBANK_ADAPTER_URL`, removed from `api-service`),
  `infra/docker/bake.hcl`, `Tiltfile` (SERVICES + PROCESSORS lists),
  `.github/workflows/release-images.yml`, `.github/workflows/prune-ghcr.yml`.
- `readme.md` - architecture diagram node + `CoP lookup` edge to the ClearBank
  adapter, and the commands-path prose.

Verified: `poly check` clean; the service `main` namespace loads against the
project classpath (all component deps resolve, system bundle registers); all
edited YAML/JSON parses.

## Decisions and risks

- Synchronous invoke holds the command consumer for the adapter round-trip.
  Fine for CoP (fast, single outbound call) and within the dispatcher's 10s
  timeout, but it is the one structural difference from `idv` (which went
  async via a later event). This is the requested behaviour and matches the
  diagram.
- FDB record type: `PayeeCheckProto$PayeeCheck` already persists today, so it is
  already in the `RecordTypeUnion`; no `fdb-record-types` change.
- Idempotency: `commands/send` already carries the idempotency key in the
  envelope (`req->command-request`); no extra work.

## File checklist (Part A, as implemented)

- New: 2 avsc schemas (`check-payee`, `payee-check`);
  `payee-check/{commands,system}.clj`; `payee-check-test.yml`;
  `payee-check/test-resources/.../application-test.yml`;
  `payee-check/test/.../interface_test.clj`.
- Edited: `payee-check/{core,interface,deps.edn}` (interface drops
  `check-payee`, now processor-internal; deps adds test-resources path);
  `api/payee_check/handlers.clj`; `api/commands.clj` (server id);
  `avro.yml` plus `avro-test.yml`; `bank-monolith` + `bank-test-api-scenarios`
  `application-test.yml` and `pulsar-test.yml`.
- Deleted code: `perform-cop-check` from `api` (moved into
  `payee-check/core.clj`).
- Verified: `payee-check` brick test (9 assertions) and full
  `test-api-scenarios` (286 assertions, all 5 CoP scenarios) pass.
</content>
</invoke>
