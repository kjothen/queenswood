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
     :orgs                 ; model-org-id → real-side metadata
       {:org-0 {:real-id  \"org.<ulid>\"
                :currency \"GBP\"}}
     :products             ; model-prod-id → real-side product metadata
       {:prod-0 {:real-id  \"prod.<ulid>\"
                 :org      :org-0
                 :versions [{:real-id \"prv.<ulid>\"
                             :status  :draft|:published|:discarded
                             :number  1}]}}
     :parties              ; model-party-id → real-side party metadata
       {:party-0 {:real-id \"party.<ulid>\"  ; auto-created org party
                  :org     :org-0}}
     :accounts             ; model-acct-id → owning model-org-id
       {:acct-0 {:org :org-0}}
     :payments             ; model-payment-id → real-side metadata
       {:pmt-0 {:real-id \"pmt.<ulid>\"}}
     :next-model-id        0  ; mirrors the model's :next-id
     :next-org-id          0  ; mirrors the model's :next-org-id
     :next-product-id      0  ; mirrors the model's :next-product-id
     :next-party-id        0  ; mirrors the model's :next-party-id
     :next-payment-id      0  ; mirrors the model's :next-payment-id
     :run-id               \"01J...\"  ; per-context idempotency-key prefix
     :counter              0  ; per-step suffix on idempotency keys
     :last-outcome         :succeeded | :denied | nil
     :last-rejection-kind  ::ns/kind | nil  ; anomaly kind on denial,
                                            ; read by :assert-rejection-kind
     :outcomes             [:succeeded :denied ...]}"
  (:require
    [com.repldriven.mono.bank-scenario-runner.id-mapping :as id-mapping]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-interest.interface :as interest]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-payment.interface :as payment]
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

(defn- seed-opened
  "Flips a freshly-opened account from `:opening` to `:opened`,
  bypassing the changelog-watcher that does this in production.
  Returns the result for tracking. Same spirit as the runner's use
  of `bank-party/seed-active-party`."
  [bank org-real-id real-acct-id]
  (cash-accounts/seed-opened-account bank org-real-id real-acct-id))

(defn- seed-closed
  "Flips a freshly-closing account from `:closing` to `:closed`,
  bypassing the changelog-watcher. Counterpart to `seed-opened`."
  [bank org-real-id real-acct-id]
  (cash-accounts/seed-closed-account bank org-real-id real-acct-id))

(defn- track
  "Records the outcome of a side-effecting step on the context.
  `:last-rejection-kind` is the anomaly kind on denial, or nil on
  success — `:assert-rejection-kind` reads it."
  [ctx result]
  (let [denied? (error/anomaly? result)
        outcome (if denied? :denied :succeeded)]
    (-> ctx
        (assoc :last-outcome outcome)
        (assoc :last-rejection-kind (when denied? (error/kind result)))
        (update :outcomes (fnil conj []) outcome))))

(defn- model-id-for-next-account
  [next-model-id]
  (keyword (str "acct-" next-model-id)))

(defn- model-id-for-next-org
  [next-org-id]
  (keyword (str "org-" next-org-id)))

(defn- model-id-for-next-product
  [next-product-id]
  (keyword (str "prod-" next-product-id)))

(defn- model-id-for-next-party
  [next-party-id]
  (keyword (str "party-" next-party-id)))

(defn- model-id-for-next-payment
  [next-payment-id]
  (keyword (str "pmt-" next-payment-id)))

(defmulti dispatch
  "Translates one command to real actions, returning the updated
  context."
  (fn [_ctx command] (:command command)))

(defmethod dispatch :open-account
  [{:keys [bank counter next-model-id next-org-id next-product-id next-party-id
           id-mapping]
    :as ctx} _command]
  (let [model-acct (model-id-for-next-account next-model-id)
        model-org (model-id-for-next-org next-org-id)
        model-prod (model-id-for-next-product next-product-id)
        model-party (model-id-for-next-party next-party-id)
        org-name (str "Scenario Customer " counter)
        result (organizations/new-organization bank
                                               org-name
                                               :organization-type-customer
                                               :organization-status-test
                                               "micro" ["GBP"])
        org (:organization result)
        real-org-id (:organization-id org)
        real-acct-id (get-in org [:accounts 0 :account-id])
        _ (when real-acct-id (seed-opened bank real-org-id real-acct-id))]
    (-> ctx
        (assoc :id-mapping (id-mapping/add id-mapping model-acct real-acct-id))
        (assoc-in [:orgs model-org] {:real-id real-org-id :currency "GBP"})
        (assoc-in [:products model-prod]
                  ;; auto-settlement product is created already-published;
                  ;; track v1 as :published so open-draft / publish-product
                  ;; eligibility match the model.
                  {:real-id (get-in org [:accounts 0 :product-id])
                   :org model-org
                   :versions [{:real-id (get-in org [:accounts 0 :version-id])
                               :status :published
                               :number 1}]})
        (assoc-in [:parties model-party]
                  {:real-id (get-in org [:party :party-id]) :org model-org})
        (assoc-in [:accounts model-acct] {:org model-org})
        (update :next-model-id inc)
        (update :next-org-id inc)
        (update :next-product-id inc)
        (update :next-party-id inc)
        (update :counter inc)
        (track result))))

(def ^:private default-balance-products
  ;; Customer-facing accounts get the standard `:default` triplet
  ;; plus interest-accrued / interest-paid so the daily accrual and
  ;; monthly capitalisation transactions can post their credit legs.
  [{:balance-type :balance-type-default :balance-status :balance-status-posted}
   {:balance-type :balance-type-default
    :balance-status :balance-status-pending-incoming}
   {:balance-type :balance-type-default
    :balance-status :balance-status-pending-outgoing}
   {:balance-type :balance-type-interest-accrued
    :balance-status :balance-status-posted}
   {:balance-type :balance-type-interest-paid
    :balance-status :balance-status-posted}])

(defn- product-payload
  "The :balance-products / scheme defaults the runner uses for any
  generated custom product."
  [product-name product-type & [extras]]
  (merge {:name product-name
          :product-type product-type
          :balance-sheet-side :balance-sheet-side-liability
          :allowed-currencies ["GBP"]
          :allowed-payment-address-schemes [:payment-address-scheme-scan]
          :balance-products default-balance-products}
         extras))

(defn- record-fresh-product
  "Records a freshly-created product (v1 draft) on the runner ctx
  when `result` is non-anomalous. Skips on anomaly."
  [ctx model-prod model-org result]
  (cond-> ctx
          (not (error/anomaly? result))
          (assoc-in [:products model-prod]
           {:real-id (:product-id result)
            :org model-org
            :versions [{:real-id (:version-id result)
                        :status :draft
                        :number 1}]})))

(defmethod dispatch :create-product
  [{:keys [bank counter next-product-id orgs] :as ctx} {[model-org] :args}]
  (let [model-prod (model-id-for-next-product next-product-id)
        {:keys [real-id]} (get orgs model-org)
        result (products/new-product bank
                                     real-id
                                     (product-payload (str "Custom Product "
                                                           counter)
                                                      :product-type-current))]
    (-> ctx
        (record-fresh-product model-prod model-org result)
        (update :next-product-id inc)
        (update :counter inc)
        (track result))))

(defn- latest-version
  "Returns the latest (highest-number) version map from a runner
  product's `:versions`."
  [product]
  (peek (:versions product)))

(defn- update-latest-version
  "Updates the latest version of `model-prod` in ctx by applying
  `f` to it. Used after a publish/discard succeeds to mirror the
  state change."
  [ctx model-prod f]
  (update-in ctx
             [:products model-prod :versions]
             (fn [versions] (conj (pop versions) (f (peek versions))))))

(defmethod dispatch :publish-product
  [{:keys [bank orgs products] :as ctx} {[model-prod] :args}]
  (let [product (get products model-prod)
        {:keys [real-id org]} product
        {version-real-id :real-id} (latest-version product)
        org-real-id (get-in orgs [org :real-id])
        result (products/publish bank org-real-id real-id version-real-id)]
    (-> ctx
        (cond-> (not (error/anomaly? result))
                (update-latest-version model-prod
                                       (fn [v] (assoc v :status :published))))
        (update :counter inc)
        (track result))))

(defmethod dispatch :open-draft
  [{:keys [bank orgs products] :as ctx} {[model-prod] :args}]
  (let [product (get products model-prod)
        {:keys [real-id org]} product
        org-real-id (get-in orgs [org :real-id])
        next-number (inc (:number (latest-version product)))
        result (products/open-draft bank
                                    org-real-id
                                    real-id
                                    (product-payload (str "Draft Version "
                                                          next-number)
                                                     :product-type-current))]
    (-> ctx
        (cond-> (not (error/anomaly? result))
                (update-in [:products model-prod :versions]
                           conj
                           {:real-id (:version-id result)
                            :status :draft
                            :number next-number}))
        (update :counter inc)
        (track result))))

(defmethod dispatch :discard-draft
  [{:keys [bank orgs products] :as ctx} {[model-prod] :args}]
  (let [product (get products model-prod)
        {:keys [real-id org]} product
        {version-real-id :real-id} (latest-version product)
        org-real-id (get-in orgs [org :real-id])
        result
        (products/discard-draft bank org-real-id real-id version-real-id)]
    (-> ctx
        (cond-> (not (error/anomaly? result))
                (update-latest-version model-prod
                                       (fn [v] (assoc v :status :discarded))))
        (update :counter inc)
        (track result))))

(defmethod dispatch :create-person-party
  [{:keys [bank counter next-party-id orgs] :as ctx}
   {[model-org ni-marker] :args}]
  (let [model-party (model-id-for-next-party next-party-id)
        {org-real-id :real-id} (get orgs model-org)
        ni (when ni-marker
             {:type :identifier-type-national-insurance
              :value (name ni-marker)
              :issuing-country "GB"})
        payload (cond-> {:organization-id org-real-id
                         :type :party-type-person
                         :display-name (str "Scenario Person " counter)
                         :given-name "Scenario"
                         :family-name (str "Person" counter)
                         :date-of-birth 19700101
                         :nationality "GB"}

                        ni
                        (assoc :national-identifier ni))
        result (party/new-party bank payload)]
    (-> ctx
        (cond->
         (not (error/anomaly? result))
         (assoc-in [:parties model-party]
          {:real-id (:party-id result)
           :org model-org}))
        (update :next-party-id inc)
        (update :counter inc)
        (track result))))

(defmethod dispatch :activate-party
  [{:keys [bank orgs parties] :as ctx} {[model-party] :args}]
  (let [{party-real-id :real-id :keys [org]} (get parties model-party)
        org-real-id (get-in orgs [org :real-id])
        result (party/seed-active-party bank org-real-id party-real-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :add-account
  [{:keys [bank counter next-model-id id-mapping orgs products parties] :as ctx}
   {[model-org model-party model-prod] :args}]
  (let [model-acct (model-id-for-next-account next-model-id)
        {org-real-id :real-id :keys [currency]} (get orgs model-org)
        {prod-real-id :real-id} (get products model-prod)
        {party-real-id :real-id} (get parties model-party)
        result (cash-accounts/new-account bank
                                          {:organization-id org-real-id
                                           :party-id party-real-id
                                           :product-id prod-real-id
                                           :currency currency
                                           :name (str "Scenario Account "
                                                      counter)})
        real-acct-id (:account-id result)
        _ (when real-acct-id (seed-opened bank org-real-id real-acct-id))]
    (-> ctx
        (cond-> real-acct-id
                (assoc :id-mapping
                       (id-mapping/add id-mapping model-acct real-acct-id)))
        (assoc-in [:accounts model-acct] {:org model-org})
        (update :next-model-id inc)
        (update :counter inc)
        (track result))))

(defmethod dispatch :close-account
  [{:keys [bank id-mapping accounts orgs] :as ctx} {[model-acct] :args}]
  (let [model-org (get-in accounts [model-acct :org])
        org-real-id (get-in orgs [model-org :real-id])
        real-acct-id (get-in id-mapping [:model->real model-acct])
        result (cash-accounts/close-account bank
                                            {:organization-id org-real-id
                                             :account-id real-acct-id})
        _ (when-not (error/anomaly? result)
            (seed-closed bank org-real-id real-acct-id))]
    (-> ctx
        (update :counter inc)
        (track result))))

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
  [{:keys [bank counter id-mapping internal-account-id run-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        result (record-and-apply
                bank
                (transfer-tx
                 {:transaction-type :transaction-type-inbound-transfer
                  :idempotency-key (str "scen-in-" run-id "-" counter)
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
  [{:keys [bank counter id-mapping internal-account-id run-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        result (record-and-apply
                bank
                (transfer-tx
                 {:transaction-type :transaction-type-outbound-transfer
                  :idempotency-key (str "scen-out-" run-id "-" counter)
                  :reference (str "scenario outbound " counter)
                  :customer-id real-id
                  :internal-account-id internal-account-id
                  :amount amount
                  :customer-side :leg-side-debit
                  :internal-side :leg-side-credit}))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :internal-transfer
  [{:keys [bank counter id-mapping run-id] :as ctx}
   {[from-model to-model amount] :args}]
  (let [from-real (id-mapping/real id-mapping from-model)
        to-real (id-mapping/real id-mapping to-model)
        result (record-and-apply
                bank
                {:idempotency-key (str "scen-int-" run-id "-" counter)
                 :transaction-type :transaction-type-internal-transfer
                 :currency "GBP"
                 :reference (str "scenario internal " counter)
                 :legs [{:account-id from-real
                         :balance-type :balance-type-default
                         :balance-status :balance-status-posted
                         :side :leg-side-debit
                         :amount amount}
                        {:account-id to-real
                         :balance-type :balance-type-default
                         :balance-status :balance-status-posted
                         :side :leg-side-credit
                         :amount amount}]})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :outbound-payment
  [{:keys [bank counter id-mapping internal-account-id orgs accounts run-id
           next-payment-id]
    :as ctx} {[model-acct amount] :args}]
  (let [real-acct-id (id-mapping/real id-mapping model-acct)
        model-org (get-in accounts [model-acct :org])
        org-real-id (get-in orgs [model-org :real-id])
        model-pmt (model-id-for-next-payment next-payment-id)
        result (payment/submit-outbound
                (assoc bank :internal-account-id internal-account-id)
                {:idempotency-key (str "scen-pay-" run-id "-" counter)
                 :organization-id org-real-id
                 :debtor-account-id real-acct-id
                 :scheme "FPS"
                 :currency "GBP"
                 :amount amount
                 :reference (str "scenario payment " counter)
                 :creditor-bban "040004000000001"
                 :creditor-name (str "Scenario Creditor " counter)})
        real-pmt-id (:payment-id result)]
    (-> ctx
        (cond-> real-pmt-id
                (assoc-in [:payments model-pmt] {:real-id real-pmt-id}))
        (cond-> real-pmt-id (update :next-payment-id inc))
        (update :counter inc)
        (track result))))

(defmethod dispatch :apply-fee
  [{:keys [bank counter id-mapping internal-account-id run-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        result (record-and-apply bank
                                 (transfer-tx
                                  {:transaction-type :transaction-type-fee
                                   :idempotency-key (str "scen-fee-" run-id
                                                         "-" counter)
                                   :reference (str "scenario fee " counter)
                                   :customer-id real-id
                                   :internal-account-id internal-account-id
                                   :amount amount
                                   :customer-side :leg-side-debit
                                   :internal-side :leg-side-credit}))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :create-savings-product
  ;; Interest-bearing variant of :create-product. Args `[:org rate-bps]`;
  ;; everything else mirrors :create-product.
  [{:keys [bank counter next-product-id orgs] :as ctx}
   {[model-org rate-bps] :args}]
  (let [model-prod (model-id-for-next-product next-product-id)
        {org-real-id :real-id} (get orgs model-org)
        result (products/new-product bank
                                     org-real-id
                                     (product-payload
                                      (str "Savings Product " counter)
                                      :product-type-savings
                                      {:interest-rate-bps rate-bps}))]
    (-> ctx
        (record-fresh-product model-prod model-org result)
        (update :next-product-id inc)
        (update :counter inc)
        (track result))))

(defmethod dispatch :accrue-interest
  [{:keys [bank orgs] :as ctx} {[model-org as-of-date] :args}]
  (let [{org-real-id :real-id} (get orgs model-org)
        result (interest/accrue-daily bank
                                      {:organization-id org-real-id
                                       :as-of-date as-of-date})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :capitalize-interest
  [{:keys [bank orgs] :as ctx} {[model-org as-of-date] :args}]
  (let [{org-real-id :real-id} (get orgs model-org)
        result (interest/capitalize-monthly bank
                                            {:organization-id org-real-id
                                             :as-of-date as-of-date})]
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

(defmethod dispatch :assert-rejection-kind
  [{:keys [last-rejection-kind] :as ctx} {[expected] :args}]
  (is (= expected last-rejection-kind)
      (str "last step rejection kind — expected " expected
           " but got " last-rejection-kind))
  ctx)

(defmethod dispatch :assert-no-anomaly
  [{:keys [outcomes] :as ctx} _command]
  (is (every? (fn [o] (= :succeeded o)) outcomes)
      (str "expected no anomalies; outcomes were " outcomes))
  ctx)
