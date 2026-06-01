(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.bank-cash-account.validation :as validation]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def default-sort-code "040004")

(defn- party->account-type
  [party]
  (if (= :party-type-person (:type party))
    :account-type-personal
    :account-type-business))

(defn- check-capability
  [action account-type policies]
  (policy/check-capability policies
                           :cash-account
                           {:action action
                            :account-type account-type}))

(defn- scan->bban
  [{:keys [sort-code account-number]}]
  (str sort-code account-number))

(defn- sub-ledger-kind-of
  "Read the sub-ledger variant fields from a product version; nil if
  this is not a sub-ledger product."
  [product-version]
  (get-in product-version [:kind :sub-ledger]))

(defn- gl-kind-of
  "Read the general-ledger variant fields from a product version; nil
  if this is not a GL product."
  [product-version]
  (get-in product-version [:kind :general-ledger]))

(defn- new-addresses
  [product-version address-fountain-fn]
  (let [{:keys [allowed-payment-address-schemes]} (sub-ledger-kind-of
                                                   product-version)]
    (cond
     (nil? allowed-payment-address-schemes)
     ;; GL products have no payment-address schemes; that's normal.
     []

     (empty? allowed-payment-address-schemes)
     (error/reject :cash-account/no-payment-schemes
                   "Product has no allowed payment address schemes")

     :else
     (reduce (fn [addresses scheme]
               (case scheme
                 :payment-address-scheme-scan
                 (let [sort-code default-sort-code
                       account-number (address-fountain-fn sort-code)]
                   (conj addresses
                         {:scheme :payment-address-scheme-scan
                          :scan {:sort-code sort-code
                                 :account-number account-number}}))

                 (reduced (error/reject :cash-account/unsupported-scheme
                                        (str
                                         "Unsupported payment address scheme: "
                                         (clojure.core/name scheme))))))
             []
             allowed-payment-address-schemes))))

(defn open-account
  "Build a cash-account record from input data and a published
  product version. The input no longer accepts GL discriminator
  fields directly; those live on the product's :kind variant.

  For sub-ledger products: derives account-type from the holder
  party and allocates payment-addresses.

  For GL products: skips the party-account-type derivation and the
  payment-address allocation; the product's variant fields drive
  everything."
  [data product-version party address-fountain-fn aggregates policies]
  (let [{:keys [bank-id party-id product-id currency name]}
        data
        {:keys [version-id]} product-version
        product-type (:product-type product-version)
        gl-kind (gl-kind-of product-version)
        account-type (when-not gl-kind (party->account-type party))]
    (let-nom>
      [_ (when (nil? product-version)
           (error/reject :cash-account/open
                         {:message "Product is not published"
                          :product-id product-id}))
       _ (validation/valid-product? product-version)
       _ (validation/valid-currency? currency product-version)
       _ (validation/valid-party? party)
       _ (when-not gl-kind
           (check-capability :cash-account-action-open
                             account-type
                             policies))
       _ (when-not gl-kind
           (policy/check-limit
            policies
            :cash-account
            {:aggregate :count
             :window :time-window-instant
             :value (inc (get-in aggregates
                                 [:cash-account #{:bank-id}]))}))
       _ (when-not gl-kind
           (policy/check-limit
            policies
            :cash-account
            {:aggregate :count
             :window :time-window-instant
             :product-type product-type
             :account-type account-type
             :currency currency
             :value (inc (get-in aggregates
                                 [:cash-account
                                  #{:bank-id :product-type
                                    :account-type :currency}]))}))
       payment-addresses (new-addresses product-version
                                        address-fountain-fn)]
      (let [now (utility/now)
            bban (some (fn [{:keys [scan]}] (when scan (scan->bban scan)))
                       payment-addresses)]
        (cond-> {:bank-id bank-id
                 :party-id party-id
                 :product-id product-id
                 :version-id version-id
                 :currency currency
                 :name name
                 :account-id (utility/generate-id "acc")
                 :account-status :cash-account-status-opening
                 :payment-addresses payment-addresses
                 :created-at now
                 :updated-at now}

                ;; product-type is set for every account — the customer
                ;; type for sub-ledger accounts, :product-type-general-
                ;; ledger for GL accounts.
                product-type
                (assoc :product-type product-type)

                ;; Customer-account-only fields
                account-type
                (assoc :account-type account-type)

                bban
                (assoc :bban bban))))))

(defn opening-balances
  [account currency product-version]
  (let [{:keys [account-id product-type]} account
        {:keys [balance-products]} (get-in product-version
                                           [:kind :sub-ledger])
        ;; GL products carry one bucket — default/posted — implicitly.
        bp (or balance-products
               [{:balance-type :balance-type-default
                 :balance-status :balance-status-posted}])]
    (mapv (fn [{:keys [balance-type balance-status]}]
            (cond-> {:account-id account-id
                     :balance-type balance-type
                     :balance-status balance-status
                     :currency currency}

                    ;; product-type mirrors the account: the customer
                    ;; type for sub-ledger accounts, general-ledger for
                    ;; GL accounts.
                    product-type
                    (assoc :product-type product-type)))
          bp)))

(defn opened-account
  [account]
  (assoc account
         :account-status :cash-account-status-opened
         :updated-at (utility/now)))

(defn close-account
  [account policies]
  (let-nom>
    [_ (check-capability :cash-account-action-close
                         (:account-type account)
                         policies)]
    (assoc account
           :account-status :cash-account-status-closing
           :updated-at (utility/now))))

(defn closed-account
  [account]
  (assoc account
         :account-status :cash-account-status-closed
         :updated-at (utility/now)))
