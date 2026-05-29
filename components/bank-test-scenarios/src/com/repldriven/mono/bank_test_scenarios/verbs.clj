(ns com.repldriven.mono.bank-test-scenarios.verbs
  (:require
    [com.repldriven.mono.bank-test-scenarios.id-mapping :as id-mapping]
    [com.repldriven.mono.bank-test-scenarios.quiescence :as quiescence]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-chart-of-accounts.interface :as
     chart-of-accounts]
    [com.repldriven.mono.bank-idv.interface :as idv]
    [com.repldriven.mono.bank-interest.interface :as interest]
    [com.repldriven.mono.bank-bank.interface :as banks]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-payee-check.interface :as payee-check]
    [com.repldriven.mono.bank-payment.interface :as payment]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-test-projections.interface :as projections]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]

    [clojure.test :refer [is]]))

(defn- record-and-apply
  "Record a transaction directly (bypassing the payment processor)
  and apply its legs to balances. Fans out customer sub-ledger legs
  to the matching control GL accounts in the same FDB transaction."
  [bank bank-id tx-data]
  (fdb/transact
   bank
   (fn [txn]
     (let [expanded (chart-of-accounts/expand-legs txn bank-id (:legs tx-data))]
       (if (error/anomaly? expanded)
         expanded
         (let [r (transactions/record-transaction
                  txn
                  (assoc tx-data :legs expanded))]
           (balances/apply-legs txn (:legs r) (:transaction-type r))))))))

(defn- seed-opened
  [bank org-real-id real-acct-id]
  (cash-accounts/seed-opened-account bank org-real-id real-acct-id))

(defn- seed-closed
  [bank org-real-id real-acct-id]
  (cash-accounts/seed-closed-account bank org-real-id real-acct-id))

(defn- track
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

(defmulti dispatch (fn [_ctx command] (:command command)))

(defmethod dispatch :create-org
  [{:keys [bank counter next-model-id next-org-id next-product-id next-party-id
           id-mapping]
    :as ctx} _command]
  (let [model-acct (model-id-for-next-account next-model-id)
        model-org (model-id-for-next-org next-org-id)
        model-prod (model-id-for-next-product next-product-id)
        model-party (model-id-for-next-party next-party-id)
        org-name (str "Scenario Customer " counter)
        result (banks/new-bank bank
                               org-name
                               :bank-type-customer :bank-status-test
                               "micro" ["GBP"])
        org (:bank result)
        real-org-id (:bank-id org)
        real-acct-id (get-in org [:accounts 0 :account-id])
        _ (when real-acct-id (seed-opened bank real-org-id real-acct-id))]
    (-> ctx
        (assoc :id-mapping (id-mapping/add id-mapping model-acct real-acct-id))
        (assoc-in [:orgs model-org] {:real-id real-org-id :currency "GBP"})
        ;; Auto-settlement product is born already-published; track v1
        ;; as :published so open-draft / publish-product eligibility
        ;; matches the model.
        (assoc-in [:products model-prod]
                  {:real-id (get-in org [:accounts 0 :product-id])
                   :org model-org
                   :versions [{:real-id (get-in org [:accounts 0 :version-id])
                               :status :published
                               :number 1}]})
        (assoc-in [:parties model-party]
                  {:real-id (get-in org [:party :party-id]) :org model-org})
        (assoc-in [:accounts model-acct]
                  {:org model-org :bban (get-in org [:accounts 0 :bban])})
        (update :next-model-id inc)
        (update :next-org-id inc)
        (update :next-product-id inc)
        (update :next-party-id inc)
        (update :counter inc)
        (track result))))

(defn- product-payload
  [product-name product-type & [extras]]
  (merge {:name product-name
          :product-type product-type
          :currency "GBP"}
         extras))

(defn- record-fresh-product
  [ctx model-prod model-org result]
  (cond-> ctx
          (not (error/anomaly? result))
          (assoc-in [:products model-prod]
           {:real-id (:product-id result)
            :org model-org
            :versions [{:real-id (:version-id result)
                        :status :draft
                        :number 1}]})))

(def ^:private product-type->kind
  {:current :product-type-current :savings :product-type-savings})

(defmethod dispatch :create-product
  [{:keys [bank counter next-product-id orgs] :as ctx}
   {[model-org type rate-bps] :args}]
  (let [model-prod (model-id-for-next-product next-product-id)
        {:keys [real-id]} (get orgs model-org)
        kind (get product-type->kind type :product-type-current)
        name
        (str (if (= :savings type) "Savings" "Current") " Product " counter)
        extras (when (and rate-bps (pos? rate-bps))
                 {:interest-rate-bps rate-bps})
        result
        (products/new-product bank real-id (product-payload name kind extras))]
    (-> ctx
        (record-fresh-product model-prod model-org result)
        (update :next-product-id inc)
        (update :counter inc)
        (track result))))

(defn- latest-version
  [product]
  (peek (:versions product)))

(defn- update-latest-version
  [ctx model-prod f]
  (update-in ctx
             [:products model-prod :versions]
             (fn [versions] (conj (pop versions) (f (peek versions))))))

(defn- resolve-product-id
  [products product-ref]
  (if (keyword? product-ref)
    (get-in products [product-ref :real-id])
    product-ref))

(defmethod dispatch :publish-product
  [{:keys [bank orgs products] :as ctx} {args :args}]
  (case (count args)
    1 (let [[model-prod] args
            product (get products model-prod)
            {:keys [real-id org]} product
            {version-real-id :real-id} (latest-version product)
            org-real-id (get-in orgs [org :real-id])
            result (products/publish bank org-real-id real-id version-real-id)]
        (-> ctx
            (cond-> (not (error/anomaly? result))
                    (update-latest-version model-prod
                                           (fn [v]
                                             (assoc v :status :published))))
            (update :counter inc)
            (track result)))
    3 (let [[model-org product-ref version-id] args
            org-real-id (get-in orgs [model-org :real-id])
            product-id (resolve-product-id products product-ref)
            result (products/publish bank org-real-id product-id version-id)]
        (-> ctx
            (update :counter inc)
            (track result)))))

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
        payload (cond-> {:bank-id org-real-id
                         :type :party-type-person
                         :display-name (str "Scenario Person " counter)
                         :given-name "Scenario"
                         :family-name (str "Person" counter)
                         :date-of-birth 19700101
                         :nationality "GB"
                         :address {:building-number "155"
                                   :street "Country Lane"
                                   :town "Cottington"
                                   :postcode "CT12 4XY"
                                   :country "GBR"}}

                        ni
                        (assoc :national-identifier ni))
        result (party/new-party bank payload)
        ;; Reality: bank-idv watcher → onfido chain → bank-party
        ;; activates. Wait until reality catches up to the model
        ;; before the next verb fires. "Scenario" given-name routes
        ;; the simulator outcome to clear → ACCEPTED, so every
        ;; non-anomaly party reaches :active.
        result' (if (error/anomaly? result)
                  result
                  (let [q (quiescence/wait-for-party-active bank
                                                            org-real-id
                                                            (:party-id result))]
                    (if (error/anomaly? q) q result)))]
    (-> ctx
        (cond->
         (not (error/anomaly? result'))
         (assoc-in [:parties model-party]
          {:real-id (:party-id result)
           :org model-org}))
        (update :next-party-id inc)
        (update :counter inc)
        (track result'))))

(defmethod dispatch :activate-party
  [{:keys [bank orgs parties] :as ctx} {[model-party] :args}]
  ;; The IDV chain auto-activates a "Scenario"-named party, so this
  ;; verb degrades to a wait-and-verify. Kept for EDN scenarios that
  ;; emit it; fugato never selects it because no parties enter
  ;; pending in the model.
  (let [{party-real-id :real-id :keys [org]} (get parties model-party)
        org-real-id (get-in orgs [org :real-id])
        result
        (quiescence/wait-for-party-active bank org-real-id party-real-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :open-account
  [{:keys [bank counter next-model-id id-mapping orgs products parties] :as ctx}
   {[model-org model-party model-prod] :args}]
  (let [model-acct (model-id-for-next-account next-model-id)
        {org-real-id :real-id :keys [currency]} (get orgs model-org)
        {prod-real-id :real-id} (get products model-prod)
        {party-real-id :real-id} (get parties model-party)
        result (cash-accounts/new-account bank
                                          {:bank-id org-real-id
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
        (assoc-in [:accounts model-acct] {:org model-org :bban (:bban result)})
        (update :next-model-id inc)
        (update :counter inc)
        (track result))))

(defmethod dispatch :close-account
  [{:keys [bank id-mapping accounts orgs] :as ctx} {[model-acct] :args}]
  (let [model-org (get-in accounts [model-acct :org])
        org-real-id (get-in orgs [model-org :real-id])
        real-acct-id (get-in id-mapping [:model->real model-acct])
        result (cash-accounts/close-account bank
                                            {:bank-id org-real-id
                                             :account-id real-acct-id})
        _ (when-not (error/anomaly? result)
            (seed-closed bank org-real-id real-acct-id))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defn- transfer-tx
  "Build a balanced 2-leg simulation transaction. `gl-leg` is the
  bank-side leg; `customer-leg` is the sub-ledger leg. Both already
  carry their own balance-type/status; the helper just wraps them
  into the transaction envelope."
  [{:keys [transaction-type idempotency-key reference currency
           gl-leg customer-leg]}]
  {:idempotency-key idempotency-key
   :transaction-type transaction-type
   :currency (or currency "GBP")
   :reference reference
   :legs [gl-leg customer-leg]})

(defn- gl-account-for
  "Look up the bank's `gl-code` GL account on its own books."
  [bank bank-id gl-code]
  (cash-accounts/get-account-by-gl-code bank bank-id gl-code))

(defn- bank-id-for-account
  "Resolve the bank-id that owns `model-acct`."
  [orgs accounts model-acct]
  (get-in orgs [(get-in accounts [model-acct :org]) :real-id]))

(defmethod dispatch :inbound-transfer
  [{:keys [bank accounts next-inbound-id run-id] :as ctx}
   {[model-acct amount] :args}]
  (let [bban (get-in accounts [model-acct :bban])
        marker (keyword (str "in-" next-inbound-id))
        stx-id (str "scen-in-" run-id "-" (name marker))
        result (payment/settle-inbound
                bank
                {:scheme-transaction-id stx-id
                 :end-to-end-id stx-id
                 :scheme "FPS"
                 :debit-credit-code :debit-credit-code-credit
                 :amount amount
                 :currency "GBP"
                 :creditor-bban bban
                 :debtor-name "Scenario Funder"
                 :reference (str "scenario inbound " (name marker))
                 :timestamp-settled (System/currentTimeMillis)})]
    (-> ctx
        (update :next-inbound-id inc)
        (update :counter inc)
        (track result))))

(defmethod dispatch :outbound-transfer
  [{:keys [bank counter id-mapping orgs accounts run-id] :as ctx}
   {[model-id amount] :args}]
  (let [real-id (id-mapping/real id-mapping model-id)
        bank-id (bank-id-for-account orgs accounts model-id)
        pending-outbound (gl-account-for bank bank-id "1200")
        result
        (if (or (nil? pending-outbound) (error/anomaly? pending-outbound))
          (error/reject :scenario/no-pending-outbound-account
                        {:message "Bank has no 1200 pending-outbound account"
                         :bank-id bank-id})
          (record-and-apply
           bank
           bank-id
           (transfer-tx {:transaction-type :transaction-type-outbound-transfer
                         :idempotency-key (str "scen-out-" run-id "-" counter)
                         :reference (str "scenario outbound " counter)
                         :gl-leg {:account-id (:account-id pending-outbound)
                                  :balance-type :balance-type-default
                                  :balance-status :balance-status-posted
                                  :side :leg-side-credit
                                  :amount amount}
                         :customer-leg {:account-id real-id
                                        :balance-type :balance-type-default
                                        :balance-status :balance-status-posted
                                        :side :leg-side-debit
                                        :amount amount}})))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :bind-policy
  [{:keys [bank orgs] :as ctx} {[model-org policy-data] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        result (let [created (policy/new-policy bank policy-data)]
                 (if (error/anomaly? created)
                   created
                   (policy/new-binding
                    bank
                    {:policy-id (:policy-id created)
                     :target {:kind {:bank {:bank-id org-real-id}}}
                     :reason "scenario-bound test policy"})))]
    (track ctx result)))

(defmethod dispatch :internal-transfer
  [{:keys [bank counter id-mapping run-id orgs accounts] :as ctx}
   {[from-model to-model amount currency] :args}]
  (let [from-real (id-mapping/real id-mapping from-model)
        to-real (id-mapping/real id-mapping to-model)
        model-org (get-in accounts [from-model :org])
        org-real-id (get-in orgs [model-org :real-id])
        result (payment/submit-internal
                bank
                {:idempotency-key (str "scen-int-" run-id "-" counter)
                 :bank-id org-real-id
                 :debtor-account-id from-real
                 :creditor-account-id to-real
                 :currency (or currency "GBP")
                 :amount amount
                 :reference (str "scenario internal " counter)})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :outbound-payment
  [{:keys [bank counter id-mapping orgs accounts run-id next-payment-id]
    :as ctx} {args :args}]
  ;; Two-arg `[debtor amount]` pays an external creditor with a
  ;; fixed BBAN. Three-arg `[debtor creditor amount]` pays a known
  ;; model account; we look its BBAN up so the bank-payment
  ;; event-processor recognises the creditor as internal and
  ;; credits it on settlement.
  ;;
  (let [internal-creditor (when (= 3 (count args)) (second args))
        [model-acct amount creditor-bban creditor-name]
        (case (count args)
          2 (let [[a amt] args]
              [a amt "040004000000001"
               (str "Scenario External Creditor " counter)])
          3 (let [[a c amt] args]
              [a amt (get-in accounts [c :bban])
               (str "Scenario Internal Creditor " counter)]))
        real-acct-id (id-mapping/real id-mapping model-acct)
        creditor-real-id (when internal-creditor
                           (id-mapping/real id-mapping internal-creditor))
        model-org (get-in accounts [model-acct :org])
        org-real-id (get-in orgs [model-org :real-id])
        model-pmt (model-id-for-next-payment next-payment-id)
        creditor-pre-net
        (when creditor-real-id
          (let [b (balances/get-balance bank
                                        creditor-real-id
                                        :balance-type-default
                                        "GBP"
                                        :balance-status-posted)]
            (when-not (error/anomaly? b) (- (:credit b 0) (:debit b 0)))))
        result (payment/submit-outbound
                bank
                {:idempotency-key (str "scen-pay-" run-id "-" counter)
                 :bank-id org-real-id
                 :debtor-account-id real-acct-id
                 :scheme "FPS"
                 :currency "GBP"
                 :amount amount
                 :reference (str "scenario payment " counter)
                 :creditor-bban creditor-bban
                 :creditor-name creditor-name})
        real-pmt-id (:payment-id result)
        ;; ClearBank settles asynchronously; the bank-payment
        ;; event-processor on schemes-payments-event fires both
        ;; settle-outbound (Debit → flip OutboundPayment to
        ;; :completed) and settle-inbound (Credit → credit the
        ;; creditor when its BBAN matches an internal account).
        ;; The model auto-completes on :outbound-payment, so the
        ;; subsequent model-eq check needs reality at the same
        ;; state. Poll the OutboundPayment for :completed (Debit
        ;; hop) — and for the 3-arg form, also poll the creditor's
        ;; balance to reach pre + amount (Credit hop, fired as a
        ;; separate transaction-settled webhook).
        _ (when real-pmt-id
            (quiescence/wait-for-outbound-completed bank real-pmt-id))
        _ (when (and real-pmt-id creditor-real-id creditor-pre-net)
            (quiescence/wait-for-credit bank
                                        creditor-real-id
                                        "GBP"
                                        (+ creditor-pre-net amount)))]
    (-> ctx
        (cond-> real-pmt-id
                (assoc-in [:payments model-pmt] {:real-id real-pmt-id}))
        (cond-> real-pmt-id (update :next-payment-id inc))
        (update :counter inc)
        (track result))))

(defmethod dispatch :wait
  [ctx {[duration-ms] :args}]
  (Thread/sleep ^long duration-ms)
  (update ctx :counter inc))

(defmethod dispatch :settle-inbound-event
  [{:keys [bank accounts] :as ctx} {[model-acct amount stx-id] :args}]
  (let [bban (get-in accounts [model-acct :bban])
        result (payment/settle-inbound
                bank
                {:scheme-transaction-id stx-id
                 :end-to-end-id stx-id
                 :scheme "FPS"
                 :debit-credit-code :debit-credit-code-credit
                 :amount amount
                 :currency "GBP"
                 :creditor-bban bban
                 :debtor-name "Scenario Funder"
                 :reference (str "scenario inbound " stx-id)
                 :timestamp-settled (System/currentTimeMillis)})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :settle-outbound-payment
  [{:keys [bank counter payments run-id] :as ctx} {[model-pmt] :args}]
  (let [real-pmt-id (get-in payments [model-pmt :real-id])
        result (payment/settle-outbound
                bank
                {:scheme-transaction-id (str "scen-stl-" run-id "-" counter)
                 :end-to-end-id real-pmt-id
                 :scheme "FPS"
                 :debit-credit-code :debit-credit-code-debit
                 :amount 0
                 :currency "GBP"
                 :creditor-bban "040004000000001"
                 :debtor-name "Scenario Settler"
                 :reference (str "scenario settlement " counter)
                 :timestamp-settled (System/currentTimeMillis)})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :apply-fee
  [{:keys [bank counter id-mapping orgs accounts run-id] :as ctx}
   {[model-id amount] :args}]
  ;; Scenario fee: DEBIT customer.default, CREDIT 1100.default
  ;; (the bank takes the fee onto its cash position — placeholder
  ;; until 4100 fee-income lands in a future wave).
  (let [real-id (id-mapping/real id-mapping model-id)
        bank-id (bank-id-for-account orgs accounts model-id)
        cash (gl-account-for bank bank-id "1100")
        result
        (if (or (nil? cash) (error/anomaly? cash))
          (error/reject :scenario/no-cash-at-correspondent-account
                        {:message "Bank has no 1100 account" :bank-id bank-id})
          (record-and-apply
           bank
           bank-id
           (transfer-tx {:transaction-type :transaction-type-fee
                         :idempotency-key (str "scen-fee-" run-id "-" counter)
                         :reference (str "scenario fee " counter)
                         :gl-leg {:account-id (:account-id cash)
                                  :balance-type :balance-type-default
                                  :balance-status :balance-status-posted
                                  :side :leg-side-credit
                                  :amount amount}
                         :customer-leg {:account-id real-id
                                        :balance-type :balance-type-default
                                        :balance-status :balance-status-posted
                                        :side :leg-side-debit
                                        :amount amount}})))]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :accrue-interest
  [{:keys [bank orgs] :as ctx} {[model-org as-of-date] :args}]
  (let [{org-real-id :real-id} (get orgs model-org)
        result (interest/accrue-daily bank
                                      {:bank-id org-real-id
                                       :as-of-date as-of-date})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :capitalize-interest
  [{:keys [bank orgs] :as ctx} {[model-org as-of-date] :args}]
  (let [{org-real-id :real-id} (get orgs model-org)
        result (interest/capitalize-monthly bank
                                            {:bank-id org-real-id
                                             :as-of-date as-of-date})]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-product
  [{:keys [bank orgs products] :as ctx} {[model-org product-ref] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        product-id (resolve-product-id products product-ref)
        result (products/get-product bank org-real-id product-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-product-version
  [{:keys [bank orgs products] :as ctx}
   {[model-org product-ref version-id] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        product-id (resolve-product-id products product-ref)
        result (products/get-version bank org-real-id product-id version-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defn- resolve-real-id
  [side-table key-or-literal]
  (if (keyword? key-or-literal)
    (get-in side-table [key-or-literal :real-id])
    key-or-literal))

(defmethod dispatch :get-account
  [{:keys [bank orgs id-mapping] :as ctx} {[model-org account-ref] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        account-id (if (keyword? account-ref)
                     (get-in id-mapping [:model->real account-ref])
                     account-ref)
        result (cash-accounts/get-account bank org-real-id account-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-party
  [{:keys [bank orgs parties] :as ctx} {[model-org party-ref] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        party-id (resolve-real-id parties party-ref)
        result (party/get-party bank org-real-id party-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-bank
  [{:keys [bank orgs] :as ctx} {[org-ref] :args}]
  (let [bank-id (resolve-real-id orgs org-ref)
        result (banks/get-bank bank bank-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-idv
  [{:keys [bank orgs] :as ctx} {[model-org verification-id] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        result (idv/get-idv bank org-real-id verification-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-payee-check
  [{:keys [bank orgs] :as ctx} {[model-org check-id] :args}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        result (payee-check/get-check bank org-real-id check-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-policy
  [{:keys [bank] :as ctx} {[policy-id] :args}]
  (let [result (policy/get-policy bank policy-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-policy-binding
  [{:keys [bank] :as ctx} {[binding-id] :args}]
  (let [result (policy/get-binding bank binding-id)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :get-balance
  [{:keys [bank id-mapping] :as ctx}
   {[account-ref balance-type currency balance-status] :args}]
  (let [account-id (if (keyword? account-ref)
                     (get-in id-mapping [:model->real account-ref])
                     account-ref)
        result (balances/get-balance bank
                                     account-id
                                     balance-type
                                     currency
                                     balance-status)]
    (-> ctx
        (update :counter inc)
        (track result))))

(defmethod dispatch :update-product-draft
  [{:keys [bank orgs products] :as ctx}
   {[model-org product-ref version-id data] :args :or {data {}}}]
  (let [org-real-id (get-in orgs [model-org :real-id])
        product-id (resolve-product-id products product-ref)
        result
        (products/update-draft bank org-real-id product-id version-id data)]
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
