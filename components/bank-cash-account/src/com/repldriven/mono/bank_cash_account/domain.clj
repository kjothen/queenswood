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

(defn- new-addresses
  [product address-fountain-fn]
  (let [{:keys [allowed-payment-address-schemes]} product]
    (if (empty? allowed-payment-address-schemes)
      (error/reject :cash-account/no-payment-schemes
                    "Product has no allowed payment address schemes")
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
  "Requires the `:cash-account-action-open` capability and
  the per-org / per-(org, product-type, account-type) count
  limits. Validates currency against version and party is
  active. Derives account-type from the party type."
  [data product party address-fountain-fn aggregates policies]
  (let [{:keys [organization-id party-id product-id currency name]} data
        {:keys [version-id product-type]} product
        account-type (party->account-type party)]
    (let-nom>
      [_ (validation/valid-product? product)
       _ (validation/valid-currency? currency product)
       _ (validation/valid-party? party)
       _ (check-capability :cash-account-action-open
                           account-type
                           policies)
       _ (policy/check-limit
          policies
          :cash-account
          {:aggregate :count
           :window :instant
           :value (inc (get-in aggregates
                               [:cash-account #{:organization-id}]))})
       _ (policy/check-limit
          policies
          :cash-account
          {:aggregate :count
           :window :instant
           :product-type product-type
           :account-type account-type
           :currency currency
           :value (inc (get-in aggregates
                               [:cash-account
                                #{:organization-id :product-type
                                  :account-type :currency}]))})
       payment-addresses (new-addresses product address-fountain-fn)]
      (let [now (utility/now)
            bban (some (fn [{:keys [scan]}] (when scan (scan->bban scan)))
                       payment-addresses)]
        {:organization-id organization-id
         :party-id party-id
         :product-id product-id
         :version-id version-id
         :currency currency
         :name name
         :account-id (utility/generate-id "acc")
         :product-type product-type
         :account-type account-type
         :account-status :cash-account-status-opening
         :payment-addresses payment-addresses
         :bban bban
         :created-at now
         :updated-at now}))))

(defn opening-balances
  "Builds the per-balance data list for a newly-opened
  account from the product's balance-products."
  [account currency product]
  (let [{:keys [account-id product-type]} account
        {:keys [balance-products]} product]
    (mapv (fn [{:keys [balance-type balance-status]}]
            {:account-id account-id
             :product-type product-type
             :balance-type balance-type
             :balance-status balance-status
             :currency currency})
          balance-products)))

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
