(ns com.repldriven.mono.bank-scenario-runner.verbs
  "Translates a model command (`{:command kw :args [...]}`) into the
  real-system actions that produce the same state change. Each verb
  takes a runner context, performs the production-side calls, and
  returns an updated context — primarily new id-mapping entries, a
  bumped counter for idempotency keys, and the outcome of the step
  (`:succeeded` or `:denied`) for assertion verbs to inspect.

  Context shape (held by the runner for one command-sequence run):

    {:bank                {:record-db ... :record-store ...}
     :internal-account-id  \"acc.<ulid>\"  ; counter-leg for transfers/fees
     :id-mapping           {:model->real {} :real->model {}}
     :next-model-id        0  ; mirrors the model's :next-id
     :counter              0  ; for unique idempotency keys
     :last-outcome         :succeeded | :denied | nil
     :outcomes             [:succeeded :denied ...]}"
  (:require
    [com.repldriven.mono.bank-scenario-runner.id-mapping :as id-mapping]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.bank-test-projections.interface :as projections]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]

    [clojure.test :refer [is]]))

(defn- record-and-apply
  "Atomically records a transaction and applies its legs."
  [bank tx-data]
  (fdb/transact
   bank
   (fn [txn]
     (let [r (transactions/record-transaction txn tx-data)]
       (balances/apply-legs txn (:legs r) (:transaction-type r))))))

(defn- track
  "Records the outcome of a side-effecting step on the context."
  [ctx result]
  (let [outcome (if (error/anomaly? result) :denied :succeeded)]
    (-> ctx
        (assoc :last-outcome outcome)
        (update :outcomes (fnil conj []) outcome))))

(defn- model-id-for-next-account
  [next-model-id]
  (keyword (str "acct-" next-model-id)))

(defmulti dispatch
  "Translates one command to real actions, returning the updated
  context."
  (fn [_ctx command] (:command command)))

(defmethod dispatch :open-account
  [{:keys [bank counter next-model-id id-mapping] :as ctx} _command]
  (let [model-id (model-id-for-next-account next-model-id)
        org-name (str "Scenario Customer " counter)
        org (organizations/new-organization bank
                                            org-name
                                            :organization-type-customer
                                            :organization-status-test
                                            "micro" ["GBP"])
        real-id (get-in org [:organization :accounts 0 :account-id])]
    (-> ctx
        (assoc :id-mapping (id-mapping/add id-mapping model-id real-id))
        (update :next-model-id inc)
        (update :counter inc)
        (track org))))

(defn- transfer-tx
  "Builds the transaction data for an inbound or outbound transfer
  between the customer's default and the internal/suspense account."
  [{:keys [transaction-type idempotency-key reference
           customer-id internal-account-id amount
           customer-side internal-side]}]
  {:idempotency-key idempotency-key
   :transaction-type transaction-type
   :currency "GBP"
   :reference reference
   :legs [{:account-id internal-account-id
           :balance-type :balance-type-suspense
           :balance-status :balance-status-posted
           :side internal-side
           :amount amount}
          {:account-id customer-id
           :balance-type :balance-type-default
           :balance-status :balance-status-posted
           :side customer-side
           :amount amount}]})

(defmethod dispatch :inbound-transfer
  [{:keys [bank counter id-mapping internal-account-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        result (record-and-apply bank
                                 (transfer-tx
                                  {:transaction-type
                                   :transaction-type-inbound-transfer
                                   :idempotency-key (str "scen-in-" counter)
                                   :reference (str "scenario inbound " counter)
                                   :customer-id real-id
                                   :internal-account-id internal-account-id
                                   :amount amount
                                   :customer-side :leg-side-credit
                                   :internal-side :leg-side-debit}))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :outbound-transfer
  [{:keys [bank counter id-mapping internal-account-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        result (record-and-apply bank
                                 (transfer-tx
                                  {:transaction-type
                                   :transaction-type-outbound-transfer
                                   :idempotency-key (str "scen-out-" counter)
                                   :reference (str "scenario outbound " counter)
                                   :customer-id real-id
                                   :internal-account-id internal-account-id
                                   :amount amount
                                   :customer-side :leg-side-debit
                                   :internal-side :leg-side-credit}))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :apply-fee
  [{:keys [bank counter id-mapping internal-account-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        result (record-and-apply bank
                                 (transfer-tx
                                  {:transaction-type :transaction-type-fee
                                   :idempotency-key (str "scen-fee-" counter)
                                   :reference (str "scenario fee " counter)
                                   :customer-id real-id
                                   :internal-account-id internal-account-id
                                   :amount amount
                                   :customer-side :leg-side-debit
                                   :internal-side :leg-side-credit}))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :assert-balance
  [{:keys [bank id-mapping] :as ctx} {[model-id expected] :args}]
  (let [actual (get (projections/project-balances bank
                                                  (:real->model id-mapping))
                    model-id)]
    (is (= expected actual) (str "balance for " model-id))
    ctx))

(defmethod dispatch :assert-outcome
  [{:keys [last-outcome] :as ctx} {[expected] :args}]
  (is (= expected last-outcome) (str "last step outcome — expected " expected))
  ctx)

(defmethod dispatch :assert-no-anomaly
  [{:keys [outcomes] :as ctx} _command]
  (is (every? (fn [o] (= :succeeded o)) outcomes)
      (str "expected no anomalies; outcomes were " outcomes))
  ctx)
