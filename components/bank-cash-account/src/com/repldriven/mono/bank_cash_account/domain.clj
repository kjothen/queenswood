(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.bank-cash-account.restriction :as restriction]
    [com.repldriven.mono.bank-cash-account.validation :as validation]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def default-sort-code "040004")

(defn- scan->bban
  [{:keys [sort-code account-number]}]
  (str sort-code account-number))

(defn- ->scan
  [sort-code account-number]
  {:scan {:sort-code sort-code :account-number account-number}})

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
                           :identifier (->scan sort-code account-number)}))

                  (reduced (error/reject :cash-account/unsupported-scheme
                                         (str
                                          "Unsupported payment address scheme: "
                                          (clojure.core/name scheme))))))
              []
              allowed-payment-address-schemes))))

(defn- party->account-type
  [party]
  (if (= :party-type-person (:type party))
    :account-type-personal
    :account-type-business))

(defn opening-account
  "Creates a new account map with status opened and payment
  addresses. Validates currency against version, party
  is active, and account count is within tier limits.
  Derives account-type from the party type."
  [data product party tier account-count address-fountain-fn]
  (let [{:keys [organization-id party-id product-id currency name]} data
        {:keys [version-id product-type]} product]
    (let-nom>
      [_ (validation/valid-product? product)
       _ (validation/valid-currency? currency product)
       _ (validation/valid-party? party)
       _ (restriction/policy-account-opening tier)
       _ (restriction/limit-max-accounts tier product-type account-count)
       payment-addresses (new-addresses product address-fountain-fn)]
      (let [now (System/currentTimeMillis)
            bban (some (fn [{:keys [identifier]}]
                         (when-let [scan (:scan identifier)]
                           (scan->bban scan)))
                       payment-addresses)]
        {:organization-id organization-id
         :party-id party-id
         :product-id product-id
         :version-id version-id
         :currency currency
         :name name
         :account-id (utility/generate-id "acc")
         :product-type product-type
         :account-type (party->account-type party)
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
         :updated-at (System/currentTimeMillis)))

(defn closing-account
  [tier account]
  (let-nom>
    [_ (restriction/policy-account-closing tier)]
    (assoc account
           :account-status :cash-account-status-closing
           :updated-at (System/currentTimeMillis))))

(defn closed-account
  [account]
  (assoc account
         :account-status :cash-account-status-closed
         :updated-at (System/currentTimeMillis)))
