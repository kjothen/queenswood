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
    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def ^:private api-key-response-keys
  [:api-key-id :key-prefix :name :created-at])

(def ^:private org-type->party-type
  {:organization-type-internal :party-type-internal
   :organization-type-customer :party-type-organization})

(def ^:private org-type->product-type
  {:organization-type-internal :product-type-internal
   :organization-type-customer :product-type-settlement})

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
  [txn org-id party-id product-id product-name currencies policies]
  (reduce (fn [acc currency]
            (let [result (cash-accounts/new-account
                          txn
                          {:organization-id org-id
                           :party-id party-id
                           :product-id product-id
                           :name product-name
                           :currency currency}
                          {:policies policies})]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc result))))
          []
          currencies))

(defn- bind-policies
  [txn org-id policies]
  (reduce (fn [_ {:keys [policy-id]}]
            (let [result (policy/new-binding
                          txn
                          {:policy-id policy-id
                           :target {:kind {:organization
                                           {:organization-id org-id}}}})]
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
  ([txn org]
   (get-organization txn org nil))
  ([txn org key-secret]
   (store/transact txn (fn [txn] (enrich txn org key-secret)))))

(defn get-organizations
  ([txn] (get-organizations txn nil))
  ([txn opts]
   (let-nom> [orgs (store/get-organizations txn opts)]
     (reduce (fn [acc org]
               (let [result (get-organization txn org)]
                 (if (error/anomaly? result)
                   (reduced result)
                   (conj acc (:organization result)))))
             []
             orgs))))

(defn get-organizations-by-type
  [txn org-type]
  (store/get-organizations-by-type txn org-type))

(defn- counts
  [txn org-type]
  (let-nom>
    [total (store/count-organizations-by-type txn org-type)]
    {:organization {#{:type} total}}))

(defn new-organization
  ([txn org-name org-type org-status tier currencies]
   (new-organization txn
                     org-name
                     org-type
                     org-status
                     tier
                     currencies
                     {}))
  ([txn org-name org-type org-status tier currencies opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (or (:policies opts)
                      (policy/get-effective-policies txn {}))
         tier-policies (if (some? tier)
                         (policy/get-policies-by-tier txn tier)
                         [])
         aggregates (counts txn org-type)
         org (domain/new-organization org-name
                                      org-type
                                      org-status
                                      aggregates
                                      policies)
         org-id (:organization-id org)

         {:keys [api-key key-secret]} (bank-api-key/new-api-key
                                       txn
                                       org-id
                                       org-status
                                       "default"
                                       {:policies policies})

         _ (store/create txn org api-key)

         {:keys [party-id]} (party/new-party
                             txn
                             {:organization-id org-id
                              :type (org-type->party-type org-type)
                              :display-name org-name}
                             {:policies policies})

         version (products/new-product
                  txn
                  org-id
                  {:name (org-type->product-name org-type)
                   :product-type (org-type->product-type org-type)
                   :balance-sheet-side :balance-sheet-side-liability
                   :allowed-currencies currencies
                   :allowed-payment-address-schemes
                   domain/allowed-payment-address-schemes
                   :balance-products (org-type->balance-products org-type)}
                  {:policies policies})
         product-id (:product-id version)
         _ (products/publish txn
                             org-id
                             product-id
                             (:version-id version)
                             {:policies policies})

         _ (open-accounts txn
                          org-id
                          party-id
                          product-id
                          (org-type->product-name org-type)
                          currencies
                          policies)

         _ (bind-policies txn org-id tier-policies)

         result (get-organization txn org key-secret)]
        result))
    :organization/create
    "Failed to create organization")))
