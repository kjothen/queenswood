(ns com.repldriven.mono.bank-cash-account.core
  (:require
    [com.repldriven.mono.bank-cash-account.domain :as domain]
    [com.repldriven.mono.bank-cash-account.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-party.interface :as parties]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.bank-tier.interface :as tiers]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- enrich-account
  [txn opts account]
  (let [{:keys [account-id]} account]
    (let-nom>
      [bals (when (:embed-balances opts)
              (balances/get-balances txn account-id))
       txns (when (:embed-transactions opts)
              (transactions/get-transactions txn account-id))]
      (cond-> account
              bals
              (merge bals)
              txns
              (assoc :transactions txns)))))

(defn- balance-data
  "Builds the per-balance data list from the account and
  product's balance-products."
  [account-id account-type currency balance-products]
  (mapv (fn [{:keys [balance-type balance-status]}]
          {:account-id account-id
           :account-type (schema/account-type->int account-type)
           :balance-type balance-type
           :balance-status balance-status
           :currency currency})
        balance-products))

(defn open-account
  "Opens an account within a transaction. Resolves published
  product version, validates currency, validates the party is
  active, then creates the account with opened status, payment
  addresses, and balances from the product's balance-products.
  Returns account map or anomaly."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [organization-id party-id product-id currency]} data]
       (let-nom>
         [tier (tiers/get-org-tier txn organization-id)
          product (products/get-published txn organization-id product-id)
          _ (when-not product
              (error/reject
               :cash-account-product/not-published
               {:message "No published product version found"
                :organization-id organization-id
                :product-id product-id}))
          party (parties/get-party txn organization-id party-id)
          account-count (store/count-accounts-by-type txn
                                                      organization-id
                                                      (:account-type product))
          account (domain/opening-account
                   data
                   product
                   party
                   tier
                   account-count
                   (fn [counter]
                     (store/allocate-payment-address txn counter)))
          _ (balances/new-balances txn
                                   (balance-data (:account-id account)
                                                 (:account-type account)
                                                 currency
                                                 (:balance-products product)))
          _ (store/save-account txn
                                account
                                {:account-id (:account-id account)
                                 :status-after (:account-status account)})]
         account)))))

(defn new-account
  "Opens a cash account with balances. Returns account map
  or anomaly."
  [txn data]
  (open-account txn data))

(defn get-account
  "Loads a single cash account, optionally embedding
  balances and transactions. Returns the account map
  or rejection anomaly if not found."
  ([txn org-id account-id]
   (get-account txn org-id account-id nil))
  ([txn org-id account-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [account (store/get-account txn org-id account-id)]
        (enrich-account txn opts account))))))

(defn get-accounts
  "Lists cash accounts for an organization, optionally
  embedding balances and transactions. Returns
  {:accounts [maps] :before id|nil :after id|nil} or
  anomaly."
  ([txn org-id]
   (get-accounts txn org-id nil))
  ([txn org-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [{:keys [accounts before after]}
         (store/get-accounts txn org-id opts)
         enriched (reduce (fn [acc account]
                            (let [result (enrich-account txn opts account)]
                              (if (error/anomaly? result)
                                (reduced result)
                                (conj acc result))))
                          []
                          accounts)]
        {:accounts enriched
         :before before
         :after after})))))

(defn get-account-by-type
  [txn org-id account-type]
  (store/get-account-by-type txn org-id account-type))

(defn get-account-by-bban
  [txn bban]
  (store/get-account-by-bban txn bban))

(defn close-account
  "Closes an account. Returns account map or anomaly."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [organization-id account-id]} data]
       (let-nom>
         [tier (tiers/get-org-tier txn organization-id)
          account (get-account txn organization-id account-id)
          updated (domain/closing-account tier account)
          _ (store/save-account txn
                                updated
                                {:account-id account-id
                                 :status-before (:account-status account)
                                 :status-after (:account-status updated)})]
         updated)))))
