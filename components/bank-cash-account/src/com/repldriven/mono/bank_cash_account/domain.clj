(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.bank-cash-account.validation :as validation]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def default-sort-code "040004")

;; Duplicated from bank-chart-of-accounts.domain to avoid a brick
;; dependency cycle (bank-chart-of-accounts already depends on
;; bank-cash-account for seeding). Keep these two maps in sync.
(def ^:private control-code-for-product-type
  {:product-type-current "2100"
   :product-type-savings "2200"
   :product-type-term-deposit "2300"})

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
  [data product party address-fountain-fn aggregates policies]
  (let [{:keys [bank-id party-id product-id currency name
                gl-code gl-account-type gl-account-class required
                gl-control-code]}
        data
        {:keys [version-id product-type]} product
        account-type (party->account-type party)
        ;; Customer accounts (current/savings/term) inherit a control
        ;; code from their product type so payments and interest
        ;; postings fan out to the matching GL control. Bank-owned
        ;; CoA accounts get :gl-code set and leave :gl-control-code
        ;; nil (they are GL themselves, not sub-ledger).
        resolved-control-code (or gl-control-code
                                  (when (nil? gl-code)
                                    (control-code-for-product-type
                                     product-type)))]
    (let-nom>
      [_ (when (nil? product)
           (error/reject :cash-account/open
                         {:message "Product is not published"
                          :product-id product-id}))
       _ (validation/valid-product? product)
       _ (validation/valid-currency? currency product)
       _ (validation/valid-party? party)
       _ (check-capability :cash-account-action-open
                           account-type
                           policies)
       _ (policy/check-limit
          policies
          :cash-account
          {:aggregate :count
           :window :time-window-instant
           :value (inc (get-in aggregates
                               [:cash-account #{:bank-id}]))})
       _ (policy/check-limit
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
                                  :account-type :currency}]))})
       payment-addresses (new-addresses product address-fountain-fn)]
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
                 :product-type product-type
                 :account-type account-type
                 :account-status :cash-account-status-opening
                 :payment-addresses payment-addresses
                 :bban bban
                 :created-at now
                 :updated-at now}

                gl-code
                (assoc :gl-code gl-code)

                gl-account-type
                (assoc :gl-account-type gl-account-type)

                gl-account-class
                (assoc :gl-account-class gl-account-class)

                required
                (assoc :required required)

                resolved-control-code
                (assoc :gl-control-code resolved-control-code))))))

(defn opening-balances
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
