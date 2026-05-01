(ns ^:eftest/synchronized
    com.repldriven.mono.bank-scenario-runner.interface-test
  (:require
    [com.repldriven.mono.bank-scenario-runner.interface :as SUT]

    [com.repldriven.mono.bank-test-model.interface :as model]
    [com.repldriven.mono.bank-test-projections.interface :as projections]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [fugato.core :as fugato]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- internal-account
  [sys]
  (-> (system/instance sys [:organizations :internal])
      (get-in [:organization :accounts 0 :account-id])))

(defn- scenario-files
  []
  (->> (io/file (.getFile (io/resource "bank-scenario-runner/scenarios")))
       (.listFiles)
       (filter (fn [f] (.endsWith (.getName f) ".edn")))
       (sort-by (fn [f] (.getName f)))))

(defn- enrich-with-org-real-id
  [model->real orgs]
  (->> model->real
       (map (fn [[model-id {:keys [real-id org]}]]
              [model-id
               {:real-id real-id
                :org-real-id (get-in orgs [org :real-id])}]))
       (into {})))

(defn- project-real
  [bank ctx]
  (let [real->model (get-in ctx [:id-mapping :real->model])]
    {:balances (projections/project-balances bank real->model)
     :products (projections/project-products
                bank
                (enrich-with-org-real-id (:products ctx) (:orgs ctx)))
     :parties (projections/project-parties
               bank
               (enrich-with-org-real-id (:parties ctx) (:orgs ctx)))
     :orgs (projections/project-orgs bank ctx)
     :accounts (projections/project-accounts bank ctx)
     :transactions (projections/project-transactions bank real->model)
     :outbound-payments (projections/project-outbound-payments
                         bank
                         (:payments ctx))}))

(defn- project-model
  [model-state]
  {:balances (projections/project-model-balances model-state)
   :products (projections/project-model-products model-state)
   :parties (projections/project-model-parties model-state)
   :orgs (projections/project-model-orgs model-state)
   :accounts (projections/project-model-accounts model-state)
   :transactions (projections/project-model-transactions model-state)
   :outbound-payments (projections/project-model-outbound-payments
                       model-state)})

(def ^:private assertion-verbs
  #{:assert-balance :assert-outcome :assert-no-anomaly})

(defn- run-with-model-check
  "Folds `steps` through the runner *and* the model in lock-step.
  After every modelled step, projects both ends and asserts they
  agree. Assertion verbs (`:assert-*`) are neutral wrt the model.
  Any other command not in the model flips `tracking?` off — real
  state has advanced in ways the model doesn't see, so further
  model comparisons would diverge for reasons unrelated to bugs.
  The runner keeps going either way so the scenario's
  hand-computed `:assert-balance` calls still run.

  Returns `{:ctx :model-eq-checks :modelled :asserts :unmodelled
            :tracking-cut-off?}` so the caller can log a per-scenario
  summary."
  [scenario-name bank internal-account-id steps]
  (loop [ctx (SUT/fresh-context bank internal-account-id)
         model-state model/init-state
         remaining steps
         tracking? true
         stats {:model-eq-checks 0
                :modelled 0
                :asserts 0
                :unmodelled 0
                :tracking-cut-off-at nil}]
    (if-let [step (first remaining)]
      (let [cmd (:command step)
            ctx' (SUT/run-commands ctx [step])
            assertion? (contains? assertion-verbs cmd)
            modelled? (contains? model/model cmd)
            tracking-after? (and tracking? (or assertion? modelled?))
            stats' (-> stats
                       (update (cond assertion?
                                     :asserts
                                     modelled?
                                     :modelled
                                     :else
                                     :unmodelled)
                               inc)
                       (cond->
                        (and tracking? (not tracking-after?))
                        (assoc :tracking-cut-off-at cmd)))]
        (if (and tracking? modelled?)
          (let [model-state' (fugato/execute model/model model-state [step])
                real (project-real bank ctx')
                expected (project-model model-state')]
            (is (= expected real)
                (str scenario-name " — model-eq after " cmd))
            (recur ctx'
                   model-state'
                   (rest remaining)
                   tracking-after?
                   (update stats' :model-eq-checks inc)))
          (recur ctx' model-state (rest remaining) tracking-after? stats')))
      (assoc stats :ctx ctx))))

(deftest scenarios-test
  (let [files (scenario-files)]
    (is (seq files) "expected scenarios on the classpath")
    (log/info "scenarios starting" {:count (count files)})
    (doseq [f files]
      (with-test-system
       [sys "classpath:bank-scenario-runner/application-test.yml"]
       (let [resource-path (str "bank-scenario-runner/scenarios/" (.getName f))]
         (nom-test> [loaded (SUT/from-resource resource-path)
                     steps (SUT/steps loaded)
                     _ (log/info "scenario running"
                                 {:file (.getName f)
                                  :name (:name loaded)
                                  :steps (count steps)})
                     stats (testing (:name loaded)
                             (run-with-model-check (:name loaded)
                                                   (fdb-config sys)
                                                   (internal-account sys)
                                                   steps))
                     _ (log/info "scenario complete"
                                 {:file (.getName f)
                                  :model-eq-checks (:model-eq-checks stats)
                                  :modelled (:modelled stats)
                                  :asserts (:asserts stats)
                                  :unmodelled (:unmodelled stats)
                                  :tracking-cut-off-at (:tracking-cut-off-at
                                                        stats)})]))))))
