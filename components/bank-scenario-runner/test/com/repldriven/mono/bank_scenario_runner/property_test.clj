(ns ^:eftest/synchronized com.repldriven.mono.bank-scenario-runner.property-test
  "Fugato-driven model-equality property test. The same runner that
  drives EDN scenarios drives generated command sequences here; on
  each trial, the model end-state and the projected real-system end-
  state must agree."
  (:require
    [com.repldriven.mono.bank-scenario-runner.interface :as SUT]

    [com.repldriven.mono.bank-test-model.interface :as model]
    [com.repldriven.mono.bank-test-projections.interface :as projections]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [fugato.core :as fugato]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- internal-account
  [sys]
  (-> (system/instance sys [:organizations :internal])
      (get-in [:organization :accounts 0 :account-id])))

(deftest model-generates-plausible-sequences-test
  (testing "fugato produces vectors of {:command :args} maps"
    (let [samples (gen/sample (fugato/commands model/model model/init-state 3)
                              5)
          known (set (keys model/model))]
      (doseq [s samples]
        (is (>= (count s) 3))
        (doseq [c s]
          (is (contains? known (:command c)))
          (is (vector? (:args c))))))))

(defn- enrich-with-org-real-id
  "Walks `:products` / `:parties` in ctx and tacks the owning org's
  real-id on each, so a projection that reads from the real bank
  can resolve `(get-product org-id prod-id)` etc. without re-doing
  the lookup. Returns `{model-id {:real-id ... :org-real-id ...}}`."
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

(defn- run-and-compare
  "Drives `cmds` through both reality and the model, then compares
  projected state across balances, products, and parties. Returns
  true on agreement; false (with the diff logged) on divergence."
  [bank internal-account-id cmds]
  (let [ctx (SUT/fresh-context bank internal-account-id)
        final (SUT/run-commands ctx cmds)
        real (project-real bank final)
        model-end (fugato/execute model/model model/init-state cmds)
        expected (project-model model-end)
        ok (= expected real)]
    (when-not ok
      (log/error "model-eq-reality divergence"
                 {:commands cmds :expected expected :real real}))
    ok))

(defn- record-trial
  [stats cmds]
  (-> stats
      (update :trials inc)
      (update :total-commands + (count cmds))
      (update :by-command
              (fn [m]
                (reduce (fn [acc c] (update acc (:command c) (fnil inc 0)))
                        m
                        cmds)))
      (update :lengths conj (count cmds))))

(defn- summarise
  [{:keys [trials total-commands by-command lengths]}]
  (let [n (max 1 trials)]
    (log/info "model-eq-reality summary"
              {:trials trials
               :total-commands total-commands
               :sequence-length (when (seq lengths)
                                  {:min (apply min lengths)
                                   :max (apply max lengths)
                                   :avg (double (/ (reduce + lengths) n))})
               :by-command (into (sorted-map)
                                 (map (fn [[cmd cnt]]
                                        [cmd
                                         {:count cnt
                                          :avg-per-trial (double
                                                          (/ cnt n))}]))
                                 by-command)})))

(def ^:private num-tests 500)
(def ^:private max-size 30)

(deftest model-eq-reality
  ;; One FDB container serves all trials; isolation comes from per-
  ;; trial fresh runner contexts (own id-mapping, own `:run-id` salt
  ;; for idempotency keys). The model resets per trial via
  ;; `fugato/execute` reducing from `init-state`; the bank accumulates
  ;; accounts across trials but the projection is keyed by the trial's
  ;; id-mapping so prior trials' accounts are invisible.
  (with-test-system
   [sys "classpath:bank-scenario-runner/application-test.yml"]
   (let [bank (fdb-config sys)
         internal (internal-account sys)
         stats (atom {:trials 0 :total-commands 0 :by-command {} :lengths []})
         _ (log/info "model-eq-reality starting"
                     {:num-tests num-tests :max-size max-size})
         result (tc/quick-check
                 num-tests
                 (prop/for-all [cmds
                                (fugato/commands model/model model/init-state)]
                               (swap! stats record-trial cmds)
                               (run-and-compare bank internal cmds))
                 :max-size
                 max-size)]
     (summarise @stats)
     (is (:result result) (str "shrunk failure: " (pr-str result))))))
