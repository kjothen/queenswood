(ns com.repldriven.mono.bank-bank.core
  (:require
    [com.repldriven.mono.bank-bank.domain :as domain]
    [com.repldriven.mono.bank-bank.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface
     :as cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface
     :as products]
    [com.repldriven.mono.bank-ledger-account.interface
     :as ledger-accounts]
    [com.repldriven.mono.identity-provider.interface
     :as identity-provider]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private default-ledger-accounts
  "The default chart of bank-owned ledger accounts every customer bank
  is seeded with, loaded once from the bank-bank resource."
  (let [path "bank-bank/ledger-accounts.edn"
        url (io/resource path)]
    (when (nil? url)
      (throw (ex-info "Default ledger-accounts resource missing" {:path path})))
    (edn/read-string (slurp url))))

(defn- new-ledger-accounts
  "Seed the customer bank's default chart of bank-owned ledger
  accounts — one `LedgerAccount` per default row per currency. Unlike
  customer cash accounts these are bank-owned and flat: no party, no
  product, no policy. Runs inside `new-bank`'s transaction, so a
  failed row rolls the whole bank creation back."
  [txn bank-id currencies]
  (reduce (fn [_ [currency row]]
            (let [result (ledger-accounts/new-account txn
                                                      bank-id
                                                      currency
                                                      row)]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          (for [currency currencies
                row default-ledger-accounts]
            [currency row])))

(defn- bind-policies
  [txn bank-id policies]
  (reduce (fn [_ {:keys [policy-id]}]
            (let [result (policy/new-binding
                          txn
                          {:policy-id policy-id
                           :target {:kind {:bank
                                           {:bank-id bank-id}}}})]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          policies))

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
  (let [bank-id (:bank-id bank)]
    (let-nom>
      [{:keys [parties]} (party/get-parties txn bank-id)
       accounts (cash-accounts/get-accounts txn bank-id)
       enriched (enrich-accounts txn bank-id (:accounts accounts))]
      (cond->
       {:bank
        (assoc bank
               :party (first parties)
               :accounts enriched
               :client-id bank-id)}

       client-secret
       (assoc :client-secret client-secret)))))

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
  ([txn bank-name bank-status tier currencies]
   (new-bank txn
             bank-name
             bank-status
             tier
             currencies
             {}))
  ([txn bank-name bank-status tier currencies opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (or (:policies opts)
                      (policy/get-effective-policies txn {}))
         tier-policies (if (some? tier)
                         (policy/get-policies-by-tier txn tier)
                         [])
         bank (domain/new-bank bank-name bank-status policies)
         bank-id (:bank-id bank)

         ;; Issue the service-account client BEFORE the FDB write so an
         ;; identity-provider failure aborts the transaction cleanly.
         ;; Client-id == bank-id (deterministic mapping). Only callers
         ;; that pass `:identity-provider` get a client. `:audience`
         ;; is the JWT `aud` claim the IDP will stamp on tokens for
         ;; this client — the bank-api handler picks it from its own
         ;; status→audience config and forwards it here.
         {:keys [client-secret]} (if-let [idp (:identity-provider opts)]
                                   (identity-provider/create-service-account
                                    idp
                                    {:bank-id bank-id
                                     :name bank-name
                                     :audience (:audience opts)})
                                   {})

         _ (store/create txn bank)

         _ (party/new-party
            txn
            {:bank-id bank-id
             :type :party-type-organization
             :display-name bank-name}
            {:policies policies})

         _ (new-ledger-accounts txn bank-id currencies)

         _ (bind-policies txn bank-id tier-policies)

         result (get-bank txn bank client-secret)]
        result))
    :bank/create
    "Failed to create bank")))
