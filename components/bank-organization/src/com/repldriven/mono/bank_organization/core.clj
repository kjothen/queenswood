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
    [com.repldriven.mono.bank-restriction.interface
     :as restriction]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

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
  [config org-id party-id product-id product-name currencies]
  (reduce (fn [acc currency]
            (let [result (cash-accounts/new-account
                          config
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
  [config accounts]
  (reduce (fn [acc account]
            (let [result (balances/get-balances config (:account-id account))]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc (merge account result)))))
          []
          accounts))

(def ^:private api-key-response-keys [:id :key-prefix :name :created-at])

(defn- get-organization
  "Enriches a flat organization map with party, accounts
  (with balances), api-key, and restrictions. When
  key-secret is provided it is included in the result for
  one-time use at creation. Returns rich organization map
  or anomaly."
  ([config org]
   (get-organization config org nil))
  ([config org key-secret]
   (let [org-id (:organization-id org)]
     (let-nom>
       [parties (party/get-parties config org-id)
        account-result (cash-accounts/get-accounts config org-id)
        enriched (enrich-accounts config
                                  (:accounts account-result))
        api-keys (bank-api-key/get-api-keys config org-id)
        restrictions (restriction/get-restrictions config
                                                   org-id)]
       (cond-> {:organization
                (assoc org
                       :party (first parties)
                       :accounts enriched
                       :api-key (select-keys (first api-keys)
                                             api-key-response-keys)
                       :policies (:policies restrictions)
                       :limits (:limits restrictions))}
               key-secret
               (assoc :key-secret key-secret))))))

(defn get-organizations
  "Lists organizations enriched with party, accounts, and
  api-key. Returns sequence of rich organization maps or
  anomaly."
  [config]
  (let-nom> [orgs (store/get-organizations config)]
    (reduce (fn [acc org]
              (let [result (get-organization config org)]
                (if (error/anomaly? result)
                  (reduced result)
                  (conj acc (:organization result)))))
            []
            orgs)))

(defn new-organization
  "Creates an organization with API key, internal party,
  product, and one cash account per currency. Returns map
  or anomaly.

  opts may include :policies and :limits to seed
  organization-level restrictions."
  [config org-name org-type currencies opts]
  (let [org (domain/new-organization org-name org-type)
        {:keys [api-key key-secret]} (bank-api-key/new-api-key
                                      (:organization-id org)
                                      "default")
        {:keys [policies limits]} opts]
    (let-nom>
      [_ (store/create config org api-key)
       org-id (:organization-id org)
       created-party (party/new-party
                      config
                      {:organization-id org-id
                       :type (org-type->party-type org-type)
                       :display-name org-name})
       product (products/new-product
                config
                org-id
                {:name (org-type->product-name org-type)
                 :account-type (org-type->account-type org-type)
                 :balance-sheet-side
                 :balance-sheet-side-liability
                 :allowed-currencies currencies
                 :allowed-payment-address-schemes
                 [:payment-address-scheme-scan]
                 :balance-products
                 (org-type->balance-products org-type)})
       product-id (get-in product [:version :product-id])
       version-id (get-in product [:version :version-id])
       _ (products/publish config org-id product-id version-id)
       _ (open-accounts config
                        org-id
                        (:party-id created-party)
                        product-id
                        (org-type->product-name org-type)
                        currencies)
       _ (when (or (seq policies) (seq limits))
           (restriction/new-restrictions
            config
            org-id
            {:organization-id org-id
             :policies policies
             :limits limits}))]
      (get-organization config org key-secret))))
