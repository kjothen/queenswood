(ns com.repldriven.mono.bank-bank.core
  (:require
    [com.repldriven.mono.bank-bank.domain :as domain]
    [com.repldriven.mono.bank-bank.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface
     :as cash-accounts]
    [com.repldriven.mono.bank-chart-of-accounts.interface
     :as chart-of-accounts]
    [com.repldriven.mono.identity-provider.interface
     :as identity-provider]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def ^:private bank-type->party-type
  {:bank-type-internal :party-type-internal
   :bank-type-customer :party-type-organization})

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

(defn- enrich-accounts
  [txn accounts]
  (reduce (fn [acc account]
            (let [result (balances/get-balances txn (:account-id account))]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc (merge account result)))))
          []
          accounts))

(defn- enrich
  [txn bank client-secret]
  (let [bank-id (:bank-id bank)]
    (let-nom>
      [{:keys [parties]} (party/get-parties txn bank-id)
       accounts (cash-accounts/get-accounts txn bank-id)
       enriched (enrich-accounts txn (:accounts accounts))]
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

(defn get-banks-by-type
  [txn bank-type]
  (store/get-banks-by-type txn bank-type))

(defn- counts
  [txn bank-type]
  (let-nom>
    [total (store/count-banks-by-type txn bank-type)]
    {:bank {#{:type} total}}))

(defn new-bank
  ([txn bank-name bank-type bank-status tier currencies]
   (new-bank txn
             bank-name
             bank-type
             bank-status
             tier
             currencies
             {}))
  ([txn bank-name bank-type bank-status tier currencies opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (or (:policies opts)
                      (policy/get-effective-policies txn {}))
         tier-policies (if (some? tier)
                         (policy/get-policies-by-tier txn tier)
                         [])
         aggregates (counts txn bank-type)
         bank (domain/new-bank bank-name
                               bank-type
                               bank-status
                               aggregates
                               policies)
         bank-id (:bank-id bank)

         ;; Issue the service-account client BEFORE the FDB write so an
         ;; identity-provider failure aborts the transaction cleanly.
         ;; Client-id == bank-id (deterministic mapping). The
         ;; internal-bank bootstrap provisions no IDP because the
         ;; queenswood bank itself authenticates as admin; only callers
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

         {:keys [party-id]} (party/new-party
                             txn
                             {:bank-id bank-id
                              :type (bank-type->party-type bank-type)
                              :display-name bank-name}
                             {:policies policies})

         ;; The internal Queenswood bank doesn't transact and has no
         ;; chart of accounts. Only customer banks seed the canonical
         ;; CoA (1100/1200/2100/2200/2300/2400/2500) on their own
         ;; organization-party.
         _ (when (= :bank-type-customer bank-type)
             (chart-of-accounts/seed! txn
                                      bank-id
                                      party-id
                                      currencies
                                      policies))

         _ (bind-policies txn bank-id tier-policies)

         result (get-bank txn bank client-secret)]
        result))
    :bank/create
    "Failed to create bank")))
