(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.bank-cash-account.restriction
     :as restriction]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

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

(defn opening-account
  "Creates a new account map with status opened and payment
  addresses. Validates currency against version, party
  is active, and account count is within tier limits."
  [data product party tier account-count address-fountain-fn]
  (let [{:keys [organization-id party-id product-id currency name]} data
        {:keys [version-id account-type]} product]
    (let-nom>
      [_ (restriction/policy-account-opening tier)
       _ (restriction/valid-currency? currency product)
       _ (restriction/valid-party? party)
       _ (restriction/limit-max-accounts tier account-type account-count)
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
         :account-id (encryption/generate-id "acc")
         :account-type account-type
         :account-status :cash-account-status-opening
         :payment-addresses payment-addresses
         :bban bban
         :created-at now
         :updated-at now}))))

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
