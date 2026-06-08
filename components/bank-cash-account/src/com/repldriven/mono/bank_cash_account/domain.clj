(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.bank-cash-account.validation :as validation]

    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility :refer [assoc-some]]))

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

(defn- check-total-limit
  [aggregates policies]
  (policy/check-limit
   policies
   :cash-account
   {:aggregate :count
    :window :time-window-instant
    :value (inc (get-in aggregates [:cash-account #{:bank-id}]))}))

(defn- check-subtotal-limit
  [product-type account-type currency aggregates policies]
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
                         #{:bank-id :product-type :account-type :currency}]))}))

(defn- scan->bban
  [{:keys [sort-code account-number]}]
  (str sort-code account-number))

(defn- new-addresses
  [product-version address-fountain-fn]
  (let [schemes (:allowed-payment-address-schemes product-version)]
    (cond
     (empty? schemes)
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
             schemes))))

(defn open-account
  "Build a cash-account record from input data and a published product
  version: derives account-type from the holder party, runs the open
  capability + count limits, and allocates payment-addresses."
  [data product-version party address-fountain-fn aggregates policies]
  (let [{:keys [bank-id party-id product-id currency name]}
        data
        {:keys [version-id]} product-version
        product-type (:product-type product-version)
        account-type (party->account-type party)]
    (let-nom>
      [_ (when (nil? product-version)
           (error/reject :cash-account/open
                         {:message "Product is not published"
                          :product-id product-id}))
       _ (validation/valid-product? product-version)
       _ (validation/valid-currency? currency product-version)
       _ (validation/valid-party? party)
       _ (check-capability :cash-account-action-open account-type policies)
       _ (check-total-limit aggregates policies)
       _ (check-subtotal-limit product-type
                               account-type
                               currency
                               aggregates
                               policies)
       payment-addresses (new-addresses product-version
                                        address-fountain-fn)]
      (let [now (utility/now)
            bban (some (fn [{:keys [scan]}] (when scan (scan->bban scan)))
                       payment-addresses)]
        (assoc-some {:bank-id bank-id
                     :party-id party-id
                     :product-id product-id
                     :version-id version-id
                     :product-type product-type
                     :account-type account-type
                     :currency currency
                     :name name
                     :account-id (utility/generate-id "acc")
                     :account-status :cash-account-status-opening
                     :payment-addresses payment-addresses
                     :created-at now
                     :updated-at now}
                    :bban
                    bban)))))

(defn opening-balances
  [account currency product-version]
  (let [{:keys [account-id product-type]} account
        ;; Fall back to a single default/posted bucket if the product
        ;; declares none.
        bp (or (:balance-products product-version)
               [{:balance-type :balance-type-default
                 :balance-status :balance-status-posted}])]
    (mapv (fn [{:keys [balance-type balance-status]}]
            {:account-id account-id
             :product-type product-type
             :balance-type balance-type
             :balance-status balance-status
             :currency currency})
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
