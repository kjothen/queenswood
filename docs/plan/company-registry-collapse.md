# Plan: collapse the company-registry indirection

Remove the registry abstraction from `company-registry` and land Companies
House on the same shape as ClearBank and Onfido: the provider is a deployment
fact, not a request parameter. Flagged as unfinished in both #275 and #276.

## What the pattern says

The vendor pattern in this workspace splits three ways, and names the vendor
only on the vendor-facing side:

- `bases/onfido-adapter` — vendor HTTP surface and command consumer
- `components/onfido-relay` — vendor egress: intent store, outbound calls
- `components/idv` — the domain concept, no provider parameter anywhere

`company-registry` inverts it. One component holds the vendor HTTP client
(`uk_companies_house.clj`), the domain store (`store.clj`), and the
indirection (`core.clj`: `default-registry`, a one-element
`available-registries`, and a `validate-registry` that is a `not=` against
that one element). The adapter base is a shell that reaches back into the
component for its client.

## The one design decision

`registry-id` becomes an input nowhere and an output in one place.

The Bank's `CompanyBinding.registry` (`banks/bank.proto:20`) stays. It is
provenance — immutable, resolved by the time the bank is created, and already
sourced server-side: `onboarding/handlers.clj:68` reads `(:registry-id
company)` off the lookup reply, never the client's `registry` body field.

So the adapter keeps stamping `registry_id` onto its reply, from a constant it
owns. Nothing branches on it; it is a signature, not a switch. This keeps
vendor identity on the vendor side, leaves `handlers.clj` untouched, and means
a future second registry adapter produces correct provenance with no API
change.

The persisted `Company` proto has no `registry_id` field and the FDB store is
named `companies`. Both are already registry-free, so none of this touches
data, the proto, or `prep`.

## 1. Drop registry-id as an input

`components/company-registry`

- Delete `default-registry`, `available-registries`, `validate-registry` from
  `core.clj`; drop the `registry-id` parameter from `lookup-company` and
  `get-company`.
- Same in `interface.clj`, including the two docstrings that describe registry
  validation and the `:company-registry/registry-not-found` anomaly.

`bases/uk-companies-house-adapter/commands.clj`

- Drop the `(or registry-id company-registry/default-registry)` default.
- Add a private `registry` constant, `"uk-companies-house"`, and assoc it onto
  the reply unconditionally.

`components/schema/resources/schemas/company/lookup-company.avsc.json`

- Remove the `registry_id` field. `company.avsc.json` keeps its `registry_id`
  — it is now the only place the value appears.

`bases/api`

- `company_registries/routes.clj` — route becomes
  `/companies/{company-number}`; drop the `registry-id` path parameter and the
  `RegistryNotFound` 404 example.
- `company_registries/queries.clj` — `lookup` loses its `registry-id`
  argument; `lookup-company` reads one path param.
- `company_registries/examples.clj` — delete `RegistryNotFound`.
- `onboarding/components.clj` — remove `[:registry {:optional true} string?]`
  from `OnboardingRequest` (it is `:closed true`, so this is a rejection for
  existing callers) and reword the docstring.
- `onboarding/handlers.clj` — `onboard` stops destructuring `registry` and
  calls `companies/lookup` with the number alone. `->binding` is unchanged.
- `onboarding/examples.clj` — drop `:registry` from the request example.
  While here, fix `":company-registry/company-not-found"` on line 33: the
  leading colon is wrong, `company_registries/examples.clj` has it right.

`bases/console`

- `api.mjs` — `lookup_company(number)`, single argument.
- `Onboarding.svelte` — remove the registry picker, `REGISTRIES`,
  `registryId`, `registryOpen`, `pickRegistry`, the "More registries coming
  soon" footer, and `registry` from the onboard body. The `idLabel` the picker
  supplied becomes a literal on the company-number field.

Tests

- `uk-companies-house-adapter/interface_test.clj` — two of its three `testing`
  blocks exist only to exercise the indirection ("a blank registry id defaults
  rather than rejecting", "an unsupported registry is rejected"). Delete both.
  Keep the round-trip, which still asserts the stamped `registry-id`.
- `company-registry/interface_test.clj` — drop the `registry` constant and the
  argument from every call; delete the registry-not-found assertions.

## 2. Move the vendor client adapter-side, rename the component

Move with `git add`, not `git rm`.

- `uk_companies_house.clj` → `bases/uk-companies-house-adapter/src/.../
  companies_house.clj`, alongside the command handler that uses it. The
  `api->company` mapping in `core.clj` goes with it — it translates the
  vendor's snake_case JSON, which is vendor knowledge.
- What is left of `core.clj` is a pass-through to `store.clj`. Collapse it:
  `interface.clj` calls the store directly.
- Rename `components/company-registry` → `components/company`, namespaces
  `com.repldriven.queenswood.company-registry.*` →
  `com.repldriven.queenswood.company.*`, and the test-resources folder to
  match. Update the root `deps.edn` (the `:local/root` entry plus the two test
  paths), `projects/monolith-service/deps.edn`, and
  `projects/uk-companies-house-adapter-service/deps.edn`.
- The adapter base gains the `http-client` dependency the component gives up.
- Anomaly keywords move namespace with the component. These are API-visible:
  they surface as the RFC 9457 `type` string. `:company-registry/
  company-not-found` → `:company/not-found`, matching `:idv/not-found`;
  `:company-registry/http`, `/parse`, `/save`, `/get` follow. Update both
  `examples.clj` files.
- `interface.clj`'s docstring is now "company records, cached from the
  registry of record" — no registry validation, no provider name.

## 3. Rename the channel and topic

`company-registries` → `companies`, matching the resource-plural convention
(`banks`, `cash-accounts`, `idv`).

- `system/company-registries.yml` → `system/companies.yml`,
  `company-registries-dispatcher.yml` → `companies-dispatcher.yml`.
- Channels `company-registries-command{,-response}` → `companies-command
  {,-response}` in both files, the API dispatcher key, and
  `bases/uk-companies-house-adapter/test-resources/.../message-bus-test.yml`.
- Topics `topic-company-registries-*` → `topic-companies-*` in
  `system/kafka-topics.yml` and `test-resources/system/kafka-test.yml`.
- The `!include` lines and dispatcher refs in
  `projects/monolith-service/resources/application.yml`, plus the producer,
  consumer and channel blocks in `projects/api-service/` and
  `projects/uk-companies-house-adapter-service/application.yml`.
- The `:companies-house-server` component and the
  `uk-companies-house-*-service` names are unchanged — those are vendor-named
  and stay that way. No infra, helm, bake or workflow churn.

Operational note: renaming a topic leaves the old one orphaned on an existing
cluster. The migrator creates the new pair on bootstrap; the old pair needs a
manual delete anywhere already deployed.

## 4. ADR-0020

Both #275 and #276 asked for it. Short: a provider is a deployment fact, so it
is chosen by which adapter service runs and which URL it is configured with,
not by a request parameter validated against a list. Pluggability is served by
the command channel — a second registry means a second adapter consuming the
same channel, not a branch inside one. The stamped `registry_id` on the reply
is provenance, not dispatch. Reference ADR-0011 (one component per third-party
library) and ADR-0019 (processor packaging).

## Verification

- `clojure -M:poly check` after each of 1–3; the rename in 2 is where it earns
  its keep.
- `clojure -M:poly test brick:company:uk-companies-house-adapter:api
  project:dev` per commit, full matrix once at the end.
- `bash scripts/hooks/enforce-idioms.sh --all`, including the include-path
  check added on this branch — commit 3 moves two `!include` targets, which is
  exactly the class of breakage it was written for.
- `just monolith-start`, then onboard through the console: the registry picker
  is gone and the created bank still carries `company-binding.registry`.

## Out of scope

- `get-company` has no production caller. The API used to serve it as a GET;
  #275 turned that into the lookup command and nothing reads the cache back.
  Either a read path returns or the store is write-only — worth deciding, but
  separately.
- `companies-command` has no `-dlq` topic where `idv-command` does. #276
  reconciled the others; this one was missed.
