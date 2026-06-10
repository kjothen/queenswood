(ns com.repldriven.mono.bank-bank.core
  (:require
    [com.repldriven.mono.bank-bank.domain :as domain]
    [com.repldriven.mono.bank-bank.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-ledger-account.interface :as ledger-accounts]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-scheduler.interface :as scheduler]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.identity-provider.interface :as identity-provider]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private default-ledger-accounts
  "The default chart of bank-owned ledger accounts every customer bank
  is seeded with, loaded once from the bank-resources classpath."
  (let [path "bank/ledgers/general-ledger.edn"
        url (io/resource path)]
    (when (nil? url)
      (throw (ex-info "Default ledger-accounts resource missing" {:path path})))
    (edn/read-string (slurp url))))

(defn- new-ledger-accounts
  "Seed the customer bank's default chart of bank-owned ledger
  accounts — one `LedgerAccount` per default row per currency. Unlike
  customer cash accounts these are bank-owned and flat: no party, no
  product. Gated on the `:ledger-account` create capability; we pass
  the bootstrap `policies` (the platform tier, which grants it) so
  seeding is allowed even for a tier that denies the capability
  per-bank. Runs inside `new-bank`'s transaction, so a failed row
  rolls the whole bank creation back."
  [txn bank-id currencies policies]
  (reduce (fn [_ [currency row]]
            (let [result (ledger-accounts/new-account txn
                                                      bank-id
                                                      currency
                                                      row
                                                      {:policies policies})]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          (for [currency currencies
                row default-ledger-accounts]
            [currency row])))

(def ^:private own-funds-template-id
  "The internal own-funds template seeded at bootstrap (see
  bank/templates/own-funds.yml); the house product is created from it."
  "tpl.00000000000000000000000004")

(defn- new-house-account
  "Create the bank's own-funds product in `currency` and open the
  house cash account under it on the bank's org party. This is the
  bank's own money: it rolls up into the 3100 own-funds control (not a
  customer-deposit control), and the bank pre-funds it so it can pay
  customers from inside the bank (rewards, etc.). An ordinary
  `CashAccount` — BBAN-addressable, transactable — so external funding
  can land in it and internal transfers can move out of it."
  [txn bank-id party-id sort-code currency policies]
  (let-nom>
    [version (products/new-product
              txn
              bank-id
              {:name "Bank own funds"
               :currency currency
               :template-id own-funds-template-id
               :effective-from (utility/today)}
              {:policies policies})
     _ (products/publish txn
                         bank-id
                         (:product-id version)
                         (:version-id version)
                         {:policies policies})]
    (cash-accounts/new-account
     txn
     {:bank-id bank-id
      :party-id party-id
      :product-id (:product-id version)
      :currency currency
      :sort-code sort-code
      :name "Bank own funds"}
     {:policies policies})))

(defn- new-house-accounts
  [txn bank-id party-id sort-code currencies policies]
  (reduce (fn [_ currency]
            (let [result (new-house-account txn
                                            bank-id
                                            party-id
                                            sort-code
                                            currency
                                            policies)]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          currencies))

(defn- bind-tier-policies
  [txn bank-id tier]
  (when-let [policies (when (some? tier)
                        (policy/get-policies-by-tier txn tier))]
    (reduce (fn [_ {:keys [policy-id]}]
              (let [result (policy/new-binding
                            txn
                            {:policy-id policy-id
                             :target {:kind {:bank
                                             {:bank-id bank-id}}}})]
                (if (error/anomaly? result) (reduced result) nil)))
            nil
            policies)))

(defn- account-gl-code
  "Resolve the GL code for an account by reading its product version's
  denormalised top-level gl-code. Nil for customer (sub-ledger)
  accounts. Lets callers select a specific GL account (e.g. the 1100
  settlement) by code rather than by seed order."
  [txn bank-id {:keys [product-id version-id]}]
  (let [version (products/get-version txn bank-id product-id version-id)]
    (when-not (error/anomaly? version)
      (:gl-code version))))

(defn- enrich-accounts
  [txn bank-id accounts]
  (reduce (fn [acc account]
            (let [bal (balances/get-balances txn (:account-id account))]
              (if (error/anomaly? bal)
                (reduced bal)
                (let [gl-code (account-gl-code txn bank-id account)
                      enriched (cond-> (merge account bal)
                                       gl-code
                                       (assoc :gl-code gl-code))]
                  (conj acc enriched)))))
          []
          accounts))

(defn- enrich
  [txn bank client-secret]
  (let [{:keys [bank-id]} bank]
    (let-nom>
      [{:keys [parties]} (party/get-parties txn bank-id)
       {:keys [accounts]} (cash-accounts/get-accounts txn bank-id)
       enriched (enrich-accounts txn bank-id accounts)]
      {:bank (assoc bank
                    :party (first parties)
                    :accounts enriched
                    :client-id bank-id)
       :client-secret client-secret})))

(defn get-bank
  ([txn bank]
   (get-bank txn bank nil))
  ([txn bank client-secret]
   (store/transact txn (fn [txn] (enrich txn bank client-secret)))))

(defn get-banks
  ([txn] (get-banks txn nil))
  ([txn opts]
   (let-nom> [banks (store/get-banks txn opts)]
     (reduce (fn [acc bank]
               (let [result (get-bank txn bank)]
                 (if (error/anomaly? result)
                   (reduced result)
                   (conj acc (:bank result)))))
             []
             banks))))

(defn new-bank
  [txn bank-name bank-status tier currencies opts]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [identity-provider company-binding]} opts]
       (let-nom>
         [_
          (when-not identity-provider
            (error/reject
             :bank/missing-identity-provider
             {:message
              "A bank requires an identity-provider to issue its service-account client"
              :bank-name bank-name}))
          policies (or (:policies opts)
                       (policy/get-effective-policies txn {}))
          sort-code (store/allocate-sort-code txn)
          ;; Snapshot of the bound legal entity (onboarding path); absent
          ;; for admin-provisioned banks.
          bank (cond-> (domain/new-bank bank-name
                                        bank-status
                                        sort-code
                                        policies)
                       company-binding
                       (assoc :company-binding company-binding))
          bank-id (:bank-id bank)

          ;; Issue the service-account client BEFORE the FDB write so an
          ;; identity-provider failure aborts the transaction cleanly.
          ;; Client-id == bank-id (deterministic mapping). `:audience` is
          ;; the JWT `aud` claim the IDP stamps on tokens for this client
          ;; — the bank-api handler picks it from its own status→audience
          ;; config and forwards it here.
          {:keys [client-secret]} (identity-provider/create-service-account
                                   identity-provider
                                   {:bank-id bank-id
                                    :name bank-name
                                    :audience (:audience opts)})
          _ (store/create txn bank)
          {:keys [party-id]} (party/new-party
                              txn
                              {:bank-id bank-id
                               :type :party-type-organization
                               :display-name bank-name}
                              {:policies policies})
          _ (new-ledger-accounts txn bank-id currencies policies)
          _ (new-house-accounts txn
                                bank-id
                                party-id
                                sort-code
                                currencies
                                policies)
          _ (bind-tier-policies txn bank-id tier)
          _ (scheduler/seed-jobs txn bank-id)
          result (get-bank txn bank client-secret)]
         result)))
   :bank/create
   "Failed to create bank"))
