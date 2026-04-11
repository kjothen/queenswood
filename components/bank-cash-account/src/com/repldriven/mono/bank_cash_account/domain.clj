(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.bank-tier.interface :as tiers]

    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- valid-currency?
  "Returns true if currency is allowed by version, rejection
  anomaly otherwise."
  [currency product]
  (let [allowed (:allowed-currencies product)]
    (if (and (seq allowed) (not (some #{currency} allowed)))
      (error/reject :cash-account/invalid-currency
                    "Currency not allowed for this product")
      true)))

(defn- valid-party?
  "Returns true if party is active, rejection anomaly
  otherwise."
  [party]
  (let [status (:status party)]
    (if (not= :party-status-active status)
      (let [s (subs (clojure.core/name status) (count "party-status-"))]
        (error/reject (keyword "cash-account"
                               (str "party-" s))
                      (str "Party is " s)))
      true)))

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
                  (let [sort-code "040004"
                        account-number (address-fountain-fn sort-code)]
                    (conj addresses
                          {:scheme :payment-address-scheme-scan
                           :identifier {:scan
                                        {:sort-code sort-code
                                         :account-number account-number}}}))
                  (reduced (error/reject :cash-account/unsupported-scheme
                                         (str
                                          "Unsupported payment address scheme: "
                                          (clojure.core/name scheme))))))
              []
              allowed-payment-address-schemes))))

(defn- policy-account-opening
  "Checks the account-opening policy. Returns nil if
  allowed, anomaly if denied or not found."
  [tier]
  (let [policy (tiers/policy tier :policy-capability-account-opening)]
    (if (error/anomaly? policy)
      policy
      (when (= :policy-effect-deny (:effect policy))
        (error/reject :cash-account/policy-denied
                      {:message (if (seq (:reason policy))
                                  (:reason policy)
                                  "Account opening denied by policy")
                       :capability :policy-capability-account-opening})))))

(defn- policy-account-closing
  "Checks the account-closing policy. Returns nil if
  allowed, anomaly if denied or not found."
  [tier]
  (let [policy (tiers/policy tier :policy-capability-account-closure)]
    (if (error/anomaly? policy)
      policy
      (when (= :policy-effect-deny (:effect policy))
        (error/reject :cash-account/policy-denied
                      {:message (if (seq (:reason policy))
                                  (:reason policy)
                                  "Account closing denied by policy")
                       :capability :policy-capability-account-closure})))))

(defn- limit-max-accounts
  "Checks the max-accounts limit for the given
  account-type against the current count. Returns nil
  if under limit or no limit defined, anomaly if
  exceeded."
  [tier account-type account-count]
  (when-let [{:keys [value reason]}
             (tiers/limit tier
                          :limit-type-max-accounts
                          {:account-type account-type})]
    (when (>= account-count value)
      (error/reject :cash-account/limit-max-accounts
                    {:message reason
                     :kind account-type
                     :limit value}))))

(defn opening-account
  "Creates a new account map with status opened and payment
  addresses. Validates currency against version, party
  is active, and account count is within tier limits."
  [data product party tier account-count address-fountain-fn]
  (let [{:keys [organization-id party-id product-id currency
                name]}
        data
        {:keys [version-id account-type]} product]
    (let-nom>
      [_ (policy-account-opening tier)
       _ (valid-currency? currency product)
       _ (valid-party? party)
       _ (limit-max-accounts tier account-type account-count)
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

(defn opening-balances
  "Returns balances for each balance-product."
  [account-id account-type currency balance-products]
  (let [now (System/currentTimeMillis)]
    (mapv (fn [{:keys [balance-type balance-status]}]
            {:account-id account-id
             :account-type (schema/account-type->int account-type)
             :balance-type balance-type
             :balance-status balance-status
             :currency currency
             :credit 0
             :debit 0
             :created-at now
             :updated-at now})
          balance-products)))

(defn opened-account
  [account]
  (assoc account
         :account-status :cash-account-status-opened
         :updated-at (System/currentTimeMillis)))

(defn closing-account
  [tier account]
  (let-nom>
    [_ (policy-account-closing tier)]
    (assoc account
           :account-status :cash-account-status-closing
           :updated-at (System/currentTimeMillis))))

(defn closed-account
  [account]
  (assoc account
         :account-status :cash-account-status-closed
         :updated-at (System/currentTimeMillis)))
