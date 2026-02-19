# CLAUDE.md

This file provides guidance to Claude Code when working with this Clojure monorepo
that follows the Polylith architecture.

## Polylith Architecture

- **Three artifact types**: Components (reusable), Bases (entry points), Projects (deployable)
- **Components** (`components/`):
  - Reusable building blocks with `interface.clj` defining public API
  - MUST defer implementation in `interface.clj` to other namespaces in the
    component, such as `core.clj`
  - MUST NOT access other components through internal namespaces — only via
    their `interface.clj`
  - MUST NOT include other components in its `deps.edn` — require other
    components through their interface namespace directly
- **Bases** (`bases/`):
  - Application entry points (e.g., APIs, readers, processors)
  - Have `-main` functions and handle application bootstrap
  - MUST NOT depend on other bases
- **Projects** (`projects/`):
  - Combine bases and components into deployable applications
  - No code, just `deps.edn` files
  - Projects do not have `-main` functions (bases do)

## Component-Based Infrastructure

- **System-as-data**: Entire systems defined in YAML/EDN configuration files: config -> system definitions -> started system
- **System construction**: Lifecycle management, through `system` component wrapping `donut.system`
- **Testcontainers**: Test infrastructure (DBs, message queues) defined in system config
- **Web Service Interceptors**: Server (`server` component) interceptors inject component instances into request context, such as datasources, MQTT clients, Pulsar consumers/producers
- **Configuration**: Env (`env` component) loading supporting profiles (:dev, :test, :prod)
- **System Multimethods**: New system components registered using `system/defcomponents` to extend system component definitions

## Error Handling

- **Anomaly-based** (nom library via `error` component):
  - Component interface functions MUST NOT throw exceptions — they MUST
    return anomalies if they fail
  - MUST NOT use `try-catch` directly; MUST use `error/try-nom` or
    `error/try-nom-ex` to catch exceptions and convert them to anomalies:

    ```clojure
    ;; Catches all exceptions
    (error/try-nom :http-client/request
                   "Failed to execute request"
                   (do-the-thing))

    ;; Catches a specific exception type
    (error/try-nom-ex :db/query
                      SQLException
                      "Failed to execute query"
                      (do-the-thing))
    ```

  - MAY use `try/finally` in special circumstances where an anomaly is not
    appropriate (e.g. ensuring resource cleanup)
  - Anomaly category reflects the call site, not the failure mode — e.g.
    `:http-client/request` not `:http-client/failed`
  - Anomaly payloads MUST contain a `:message` key. Pass a string as
    shorthand — `(error/fail :ns/x "message")` — or a map for additional
    context — `(error/fail :ns/x {:message "..." :account-id id})`

- **Common functions**:
  - _Predicates and construction_:
    - `error/anomaly?` - check if value is anomaly
    - `error/fail` - create anomaly with category and details map
  - _Exception catching_:
    - `error/try-nom` - wrap body, catching all exceptions as anomalies
    - `error/try-nom-ex` - wrap body, catching a specific exception type
  - _Let-style bindings_:
    - `error/let-nom>` - monadic let, short-circuits on first binding anomaly
    - `error/nom-let>` - like `let-nom>` but calls error-fn if result is
      anomaly
  - _Threading and side effects_:
    - `error/nom->` - threading macro, short-circuits on anomalies
    - `error/nom-do>` - execute operations sequentially, short-circuit and
      call error-fn on first anomaly

## Testing

### Running Tests

- **Default project**: Always use `project:dev` unless a specific project is
  requested
- **Running all tests**:

  ```bash
  clojure -M:poly test project:dev
  ```

- **Testing specific bricks**:

  ```bash
  clojure -M:poly test brick:<brick-name> project:dev
  ```

### Writing Tests

- **No test fixtures**: Do not use `use-fixtures` — manage lifecycle explicitly
  with `system/with-system` instead
- **Test resources**: Shared config lives in the `test-resources` component.
  Each brick combines this with its own
  `test-resources/<brick>/application-test.yml`
- **system/with-system**: Binding-based macro for test system lifecycle:

  ```clojure
  (system/with-system [sys (test-system)]
    (let [component (system/instance sys [:path :to :component])]
      ;; test code
      ))
  ```

- **nom-let> pattern**: chain operations, fail fast on anomaly:

  ```clojure
  (error/nom-let>
    [result1 (operation1)
     _ (is (= expected result1))
     result2 (operation2 result1)]
    test/refute-anomaly)
  ```

- **nom-> + refute-anomaly**: for simple sequential checks without bindings:

  ```clojure
  (test/refute-anomaly
   (error/nom-> (first-operation)
                (second-operation)))
  ```

- **Test runner**: eftest runs tests in parallel out of process. Mark expensive
  infrastructure tests with `^:eftest/synchronized` to prevent too many from
  overwhelming CPU/memory

## Database Patterns

- **db component**: PostgreSQL integration
- **migrator component**: Liquibase-based migrations
- **Connection pooling**: Managed by system component lifecycle
- **Testcontainers**: Spin up PostgreSQL in tests via system config

## Message Queue Patterns

- **pulsar component**: Apache Pulsar integration with Avro serialization
- **Stopping**: Send to `:stop` channel to stop receiving/reading
- **mqtt component**: MQTT client integration for request-reply patterns
- **command component**: Higher-level command processing over Pulsar/MQTT
  - **Channel-based async**: Both `receive` (consumer) and `read` (reader)
    return `{:c chan :stop chan}`
  - **Message format**: `{:message <Message> :data <deserialized-data>}` on
    `:c` channel
  - `command/process` - consumes commands from Pulsar, dispatches to a
    process-fn, publishes replies via MQTT. Returns `{:stop chan}`
  - `command/send` - sends a command via Pulsar, awaits reply via MQTT.
    Returns response map or anomaly
  - `command/req->command-request` - builds wire command map from HTTP request
  - `command/req->command-response` - builds command response from HTTP request
    and result (anomaly-aware)

## Code Formatting

- **zprint**: All Clojure source is formatted with zprint, configured in `.zprint.edn`
- **Width**: 80 characters
- **Git hook**: `scripts/hooks/pre-commit` automatically formats staged Clojure
  files before each commit. Install it once with:

  ```bash
  cp scripts/hooks/pre-commit .git/hooks/pre-commit
  chmod +x .git/hooks/pre-commit
  ```

- **Namespaces**: MUST `:require` entries innermost to outermost —
  excepting indirect interfaces which extend multi-methods MUST take precedence
  (removing [] to make it obvious) unless they need to required by alias too -
  then internal namespaces, then other component interfaces (and interfaces **ONLY**),
  then external libraries, then standard libraries, separated by line-breaks. For
  component interface tests, use MUST use the `SUT` alias for the component interface,
  and MUST NOT include any other namespaces from the component.

  ```clojure
  (ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
    (:require
      com.repldriven.mono.testcontainers.interface  ;; extends `system/components`

      [com.repldriven.mono.processor.interface :as SUT]

      [com.repldriven.mono.error.interface :as error]
      [com.repldriven.mono.system.interface :as system]
      [com.repldriven.mono.test.interface :as test]
      [com.repldriven.mono.db.interface :as sql]
      [com.repldriven.mono.env.interface :as env]
      [com.repldriven.mono.json.interface :as json]

      [clojure.test :refer [deftest is testing]]))
  ```

- **Docstrings**: zprint does not reflow string content, so docstrings must be
  manually wrapped at 80 characters. Write multi-line docstrings like:

  ```clojure
  (defn my-fn
    "First line of docstring, kept within 80 characters.

    Further detail on subsequent lines, also wrapped at 80 chars. Use
    blank lines to separate paragraphs."
    [args]
    body)
  ```

## Code Linting

- **clj-kondo**: Configured in `.clj-kondo/config.edn` with lint-as mappings for macros
- **Git hook**: `scripts/hooks/pre-commit` also runs clj-kondo against the full
  `bases`, `components`, and `projects` directories when any Clojure files
  are staged, blocking the commit if lint errors are found

## Coding Guidelines

- **Referential transparency**: For an expression to be referentially
  transparent, we must be able to bind the expression to a name, substitute
  that name for any or all occurrences of the original expression (within the
  same context), and nothing should have changed (except perhaps the execution
  time). Prefer pure functions that return the same value for the same inputs,
  with no observable side effects. Name functions after what they return, not
  what they do.
- **Wire data uses string keys**: Message data from external systems (Pulsar,
  MQTT, HTTP, JSON) is handled with string keys throughout. Do not convert
  wire data to keyword keys — use `{:strs [...]}` destructuring and string
  literals for map access. Converting between string and keyword keys at
  different layers causes confusion about data provenance.
- **Naming**: Naming is hard, so try not to name at all by using thread macros.
  Names should be narrow. "Elements of Clojure" by Zachary Tellman gets _everything_
  right about names.
