# Queenswood

Core banking as a service — organisations, parties, identity verification,
cash account products, account lifecycle, double-entry transactions,
inbound and outbound payments via ClearBank FPS, and interest accrual
with fractional carry.

[![Queenswood Bank](thumbnail.png)](https://github.com/user-attachments/assets/d6941c18-54c6-4954-aa7d-b8150f5d2891)

| Capability                   | Description                                                                                                                                                       |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Organisations & API Keys** | Multi-tenant onboarding — create a tenant, issue API keys (returned once, stored hashed)                                                                          |
| **Cash Account Products**    | Draft products with balance configurations, publish versioned releases                                                                                            |
| **Parties & Identity**       | Register customers with national identifiers; automatic IDV triggers `pending` → `active`                                                                         |
| **Cash Accounts**            | Open accounts against published products, assigned UK SCAN payment addresses (sort code + account number). Lifecycle: `opening` → `opened` → `closing` → `closed` |
| **Payments & Transactions**  | Internal transfers, outbound FPS payments via ClearBank, inbound payment settlement with BBAN lookup and idempotency                                              |
| **Interest**                 | Daily accrual and monthly capitalisation with fractional carry at sub-minor-unit precision                                                                        |

Full API documentation:
[kjothen.github.io/queenswood](https://kjothen.github.io/queenswood/)
| OpenAPI locally at [localhost:8080](http://localhost:8080) when running

## Architecture

```mermaid
graph TD
    APP["bank-app<br/>(Svelte)"]
    API["bank-api<br/>(Reitit + Malli)"]
    SIM["bank-clearbank-simulator<br/>(ClearBank FPS mock)"]
    ADAPTER["bank-clearbank-adapter<br/>(webhooks + FPS client)"]
    APP -->|HTTP API| API

    subgraph sync ["Direct (create/update/query)"]
        SYNC_CU["api-key<br/>cash-account-product<br/>organization<br/>tier"]
        SYNC_Q["query: *"]
    end

    subgraph async ["Commands (create/update/simulate)"]
        CP["command-processor<br/>(Avro)"]
        CP --> PARTY[party]
        CP --> CASH[cash-account]
        CP --> PAY[payment]
        CP --> INT[interest]
        CP --> TXN[transaction]
    end

    subgraph events ["Async Message-Bus (events)"]
        EP["event-processor<br/>(Avro)"]
        EP -->|transaction-settled<br/>credit| PAY_IN["payment<br/>(settle inbound)"]
        EP -->|transaction-settled<br/>debit| PAY_OUT["payment<br/>(settle outbound)"]
    end

    API --> sync
    API --> async

    PAY -->|submit-payment| ADAPTER
    ADAPTER -->|POST /v3/payments/fps| SIM
    SIM -->|TransactionSettled<br/>webhook| ADAPTER
    ADAPTER -->|publish event| events

    subgraph watchers ["Watchers (FDB changelog)"]
        W1["idv: → accepted"]
        W2["party: → active"]
        W3["cash-account: → opened / closed"]
    end

    PARTY --> W1
    PARTY --> W2
    CASH --> W3

    subgraph fdb ["FoundationDB (Record Layer)"]
        STORES["api-keys<br/>balances<br/>cash-account-products<br/>cash-accounts<br/>idvs<br/>inbound-payments<br/>internal-payments<br/>organizations<br/>outbound-payments<br/>parties<br/>tiers<br/>transactions"]
    end

    sync --> fdb
    watchers --> fdb
    PARTY --> fdb
    CASH --> fdb
    PAY --> fdb
    PAY_IN --> fdb
    PAY_OUT --> fdb
    INT --> fdb
    TXN --> fdb
```

**Direct path** — low-volume activity, concerning organisations, products,
tiers and API keys are created/updated directly by the API handlers.
All records are queried on-demand using FDB record primary key ordering.

**Commands path** — high volume activity, concerning parties,
cash accounts and payments are Avro-serialised commands
sent through message bus to command processors.
Processors write to FDB and reply via message bus.
Responses use envelope statuses:
`ACCEPTED` (2xx), `REJECTED` (4xx), or `FAILED` (5xx).

**Events path** — outbound payments publish a `submit-payment`
command to the ClearBank adapter, which POSTs to the FPS API.
Settlement webhooks are published as `transaction-settled` events;
the event processor routes credits to inbound payment creation
and debits to outbound payment status completion.

**Watchers** — FDB changelog triggers drive reactive state transitions:
IDV acceptance activates the party; account opening/closing auto-transitions.

## How It Works

Queenswood is a **domain fork of [mono](https://github.com/kjothen/mono)**,
a Clojure component library for building production-ready distributed
systems with [Polylith](https://polylith.gitbook.io/polylith). It is
assembled from mono's infrastructure primitives:

- **FoundationDB Record Layer** stores organisations, parties, IDV records,
  account products, accounts, inbound/outbound/internal payments,
  transactions and balances with multi-store FDB transactions for atomicity.
- **Changelog watchers** on FDB drive the reactive flow — IDV acceptance
  activates the party; account closing auto-transitions to closed.
- **Apache Pulsar** carries commands and events between the HTTP API,
  processors, and the ClearBank adapter, with Avro-serialised messages.
- **ClearBank adapter** receives settlement webhooks and submits outbound
  FPS payments. A local simulator replaces the real ClearBank API in
  development and test.
- **Reitit + Malli** provide routing, schema validation, and OpenAPI spec
  generation.
- The whole system — containers, message brokers, databases — is declared in
  YAML and started by the same lifecycle machinery used in tests.

## Running It

Start a REPL with `just repl` and connect your editor. The development
entry point follows the standard Polylith pattern — a namespace under
`development/src/dev/` that requires the base and Testcontainers:

```clojure
;; development/src/dev/bank_monolith.clj — evaluate the comment block
(def sys
  (main/start "classpath:bank-monolith/application-test.yml"
              :dev))
(main/stop sys)
```

This boots the full system — FDB, Pulsar, HTTP server — inside
Testcontainers. Then start the Svelte front-end:

```bash
just start-bank-app
```

## Upstream Components

The shared component library (lifecycle, persistence, messaging, security,
etc.) is documented in the
[mono README](https://github.com/kjothen/mono#mono-components).

## Components

| Component &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Purpose                                                                                            |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `bank-api-key`                                                                                                                                             | API key generation, hashing, and verification                                                      |
| `bank-balance`                                                                                                                                             | Account balance management — create, query by type/currency/status                                 |
| `bank-cash-account`                                                                                                                                        | Account lifecycle — open, close, suspend, reopen, archive                                          |
| `bank-cash-account-product`                                                                                                                                | Product and version management — draft, publish, balance product config                            |
| `bank-clearbank-webhook`                                                                                                                                   | ClearBank webhook Malli schemas and OpenAPI examples                                               |
| `bank-idv`                                                                                                                                                 | Identity verification processing                                                                   |
| `bank-interest`                                                                                                                                            | Interest accrual and capitalization with fractional carry                                           |
| `bank-organization`                                                                                                                                        | Organisation management — create org, API key generation and verification                          |
| `bank-party`                                                                                                                                               | Party creation and management                                                                      |
| `bank-payment`                                                                                                                                             | Payment processing — internal, inbound (FPS credit), and outbound (FPS debit)                      |
| `bank-schema`                                                                                                                                              | Protobuf and Avro definitions for all record types and payment schemes                             |
| `bank-test-resources`                                                                                                                                      | Bank-specific test configuration (FDB stores, Avro schemas)                                        |
| `bank-tier`                                                                                                                                                | Organisation tier system — policies and limits                                                     |
| `bank-transaction`                                                                                                                                         | Transaction recording with double-entry legs                                                       |

## Bases

| Base                       | Purpose                                                          |
| -------------------------- | ---------------------------------------------------------------- |
| `bank-api`                 | HTTP API handlers, routes, and OpenAPI spec                      |
| `bank-app`                 | Svelte front-end for the banking application                     |
| `bank-clearbank-adapter`   | ClearBank integration — webhook reception and FPS payment client |
| `bank-clearbank-simulator` | ClearBank FPS mock — payment API and webhook firing              |
| `bank-monolith`            | Full system entry point combining all servers and processors     |

## Projects

| Project                     | Base            | Description                                   |
| --------------------------- | --------------- | --------------------------------------------- |
| `bank-app`                  | `bank-app`      | Svelte front-end for the banking application  |
| `bank-cash-account-service` | `service`       | Async command handler for account operations  |
| `bank-monolith`             | `bank-monolith` | Full Queenswood system (API + processors)     |
| `bank-web`                  | `bank-api`      | HTTP API for accounts, products, and balances |
