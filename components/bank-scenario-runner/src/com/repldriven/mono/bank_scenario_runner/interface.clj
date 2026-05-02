(ns com.repldriven.mono.bank-scenario-runner.interface
  "Drives a model command sequence (from fugato or an EDN scenario)
  against a real bank. Owns the model→real ID side-table, the verb
  dispatcher, the assertion verbs, and the quiescence wait used
  before projecting state for comparison.

  See `docs/design/scenario-testing.md`."
  (:require
    [com.repldriven.mono.bank-scenario-runner.id-mapping :as id-mapping]
    [com.repldriven.mono.bank-scenario-runner.quiescence :as quiescence]
    [com.repldriven.mono.bank-scenario-runner.scenario :as scenario]
    [com.repldriven.mono.bank-scenario-runner.verbs :as verbs]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as util]))

(defn fresh-context
  "Initial context for one command-sequence run. `bank` is the FDB
  config map (`:record-db` / `:record-store`); `internal-account-id`
  is the platform's internal/suspense account, used as the
  counter-leg for transfers and fees. The `:run-id` (a fresh
  uuidv7) prefixes idempotency keys so multiple runs against the
  same bank don't collide on the dedup index."
  [bank internal-account-id]
  {:bank bank
   :internal-account-id internal-account-id
   :id-mapping id-mapping/init
   :orgs {}
   :products {}
   :parties {}
   :accounts {}
   :payments {}
   :next-model-id 0
   :next-org-id 0
   :next-product-id 0
   :next-party-id 0
   :next-payment-id 0
   :next-inbound-id 0
   :run-id (str (util/uuidv7))
   :counter 0
   :outcomes []})

(defn run-commands
  "Dispatches each command in `commands` against the real bank,
  threading the runner context through. Waits for read-side
  quiescence before returning. Returns the final context — the
  caller pulls the id-mapping out of `:id-mapping` to feed into a
  projection."
  [ctx commands]
  (let [final (reduce verbs/dispatch ctx commands)]
    (quiescence/wait (:bank final))
    final))

(defn run-scenario
  "Loads and validates the EDN scenario at `resource-path` (a
  classpath resource) and runs every step through the same
  dispatch as `run-commands`. Returns the final context, or an
  anomaly if loading/validation fails. Assertion steps inside the
  scenario fire `clojure.test/is` on dispatch."
  [bank internal-account-id resource-path]
  (let [loaded (scenario/from-resource resource-path)]
    (if (error/anomaly? loaded)
      loaded
      (run-commands (fresh-context bank internal-account-id)
                    (scenario/steps loaded)))))

(def from-resource scenario/from-resource)
(def steps scenario/steps)
