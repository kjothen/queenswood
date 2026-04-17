(ns com.repldriven.mono.bank-organization.core
  (:require
    [com.repldriven.mono.bank-organization.domain :as domain]
    [com.repldriven.mono.bank-organization.store :as store]

    [com.repldriven.mono.bank-api-key.interface :as bank-api-key]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface
     :as cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface
     :as products]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-tier.interface :as tiers]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def ^:private api-key-response-keys
  [:api-key-id :key-prefix :name :created-at])

(def ^:private org-type->party-type
  {:organization-type-internal :party-type-internal
   :organization-type-customer :party-type-organization})

(def ^:private org-type->account-type
  {:organization-type-internal :account-type-internal
   :organization-type-customer :account-type-settlement})

(def ^:private org-type->product-name
  {:organization-type-internal "Internal Account"
   :organization-type-customer "Settlement Account"})

(def ^:private org-type->balance-products
  {:organization-type-internal [{:balance-type :balance-type-default
                                 :balance-status :balance-status-posted}
                                {:balance-type :balance-type-suspense
                                 :balance-status :balance-status-posted}]
   :organization-type-customer [{:balance-type :balance-type-default
                                 :balance-status :balance-status-posted}
                                {:balance-type :balance-type-interest-payable
                                 :balance-status :balance-status-posted}]})

(defn- open-accounts
  "Opens one cash account per currency. Returns vector of
  accounts or anomaly."
  [txn org-id party-id product-id product-name currencies]
  (reduce (fn [acc currency]
            (let [result (cash-accounts/new-account
                          txn
                          {:organization-id org-id
                           :party-id party-id
                           :product-id product-id
                           :name product-name
                           :currency currency})]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc result))))
          []
          currencies))

(defn- enrich-accounts
  "Attaches balances to each account. Returns enriched
  accounts or anomaly."
  [txn accounts]
  (reduce (fn [acc account]
            (let [result (balances/get-balances txn (:account-id account))]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc (merge account result)))))
          []
          accounts))

(defn- enrich
  "Enriches a flat organization map with party, accounts
  (with balances), and api-key. Returns rich organization
  map or anomaly. key-secret is included only when freshly
  minted."
  [txn org key-secret]
  (let [org-id (:organization-id org)]
    (let-nom>
      [{:keys [parties]} (party/get-parties txn org-id)
       accounts (cash-accounts/get-accounts txn org-id)
       enriched (enrich-accounts txn (:accounts accounts))
       api-keys (bank-api-key/get-api-keys txn org-id)]
      (cond->
       {:organization
        (assoc org
               :party (first parties)
               :accounts enriched
               :api-key (select-keys (first api-keys) api-key-response-keys))}

       key-secret
       (assoc :key-secret key-secret)))))

(defn get-organization
  "Enriches a flat organization map with party, accounts
  (with balances), and api-key. Returns rich organization
  map or anomaly."
  ([txn org]
   (get-organization txn org nil))
  ([txn org key-secret]
   (store/transact txn (fn [txn] (enrich txn org key-secret)))))

(defn get-organizations
  "Lists organizations enriched with party, accounts, and
  api-key. Returns sequence of rich organization maps or
  anomaly."
  [txn]
  (let-nom> [orgs (store/get-organizations txn)]
    (reduce (fn [acc org]
              (let [result (get-organization txn org)]
                (if (error/anomaly? result)
                  (reduced result)
                  (conj acc (:organization result)))))
            []
            orgs)))

(defn get-organizations-by-type
  "Lists organizations matching the given type. Returns
  a sequence of organization maps or anomaly."
  [txn org-type]
  (store/get-organizations-by-type txn org-type))

(defn new-organization
  "Creates an organization with API key, party,
  product, and one cash account per currency. Returns
  map or anomaly."
  [txn org-name org-type tier-type currencies]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [tier (tiers/get-tier txn tier-type)

        org-count (store/count-organizations-by-type txn org-type)
        org (domain/new-organization org-name org-type tier org-count)
        org-id (:organization-id org)

        {:keys [api-key key-secret]} (bank-api-key/new-api-key org-id
                                                               "default")

        _ (store/create txn org api-key)

        {:keys [party-id]} (party/new-party
                            txn
                            {:organization-id org-id
                             :type (org-type->party-type org-type)
                             :display-name org-name})

        product (products/new-product
                 txn
                 org-id
                 {:name (org-type->product-name org-type)
                  :account-type (org-type->account-type org-type)
                  :balance-sheet-side :balance-sheet-side-liability
                  :allowed-currencies currencies
                  :allowed-payment-address-schemes
                  domain/allowed-payment-address-schemes
                  :balance-products (org-type->balance-products org-type)})
        product-id (get-in product [:version :product-id])
        _ (products/publish txn org-id product-id)

        _ (open-accounts txn
                         org-id
                         party-id
                         product-id
                         (org-type->product-name org-type)
                         currencies)

        result (get-organization txn org key-secret)]
       result))
   :organization/create
   "Failed to create organization"))
