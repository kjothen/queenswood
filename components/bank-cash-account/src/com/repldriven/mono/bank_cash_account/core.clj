(ns com.repldriven.mono.bank-cash-account.core
  (:require
    [com.repldriven.mono.bank-cash-account.domain :as domain]
    [com.repldriven.mono.bank-cash-account.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-party.interface :as parties]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- get-policies
  ([txn org-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn {:organization-id org-id})))
  ([txn org-id account-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn
                                      {:organization-id org-id
                                       :account-id account-id}))))

(defn- enrich-account
  [txn opts account]
  (let [{:keys [account-id]} account]
    (let-nom>
      [balances (when (:embed-balances opts)
                  (balances/get-balances txn account-id))
       transactions (when (:embed-transactions opts)
                      (transactions/get-transactions txn account-id))]
      (cond-> account

              balances
              (merge balances)

              transactions
              (assoc :transactions transactions)))))

(defn- party->account-type
  [party]
  (if (= :party-type-person (:type party))
    :account-type-personal
    :account-type-business))

(defn- counts
  "Builds the cash-account aggregates map for the limit checks
  in `domain/open-account`. Each entry is keyed by the set of
  dimensions the count is grouped on."
  [txn org-id product-type account-type currency]
  (let-nom>
    [total (store/count-by-org txn org-id)
     subtotal (store/count-by-org-product-account-type-currency
               txn
               org-id
               product-type
               account-type
               currency)]
    {:cash-account
     {#{:organization-id} total
      #{:organization-id :product-type :account-type :currency} subtotal}}))

(defn open-account
  "Opens a cash account with balances. opts supports
  `:policies` to override policy resolution."
  ([txn data]
   (open-account txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [organization-id party-id product-id currency]} data]
        (let-nom>
          [policies (get-policies txn organization-id opts)
           party (parties/get-party txn organization-id party-id)
           product (products/get-product txn
                                         organization-id
                                         product-id)
           product-version (products/published-version product)
           _ (when (nil? product-version)
               (error/reject :cash-account/open
                             {:message "Product is not published"
                              :product-id product-id}))
           aggregates (counts txn
                              organization-id
                              (:product-type product-version)
                              (party->account-type party)
                              currency)
           account (domain/open-account
                    data
                    product-version
                    party
                    (fn [counter]
                      (store/allocate-payment-address txn counter))
                    aggregates
                    policies)
           _ (balances/new-balances
              txn
              (domain/opening-balances account currency product-version))
           _ (store/save-account txn
                                 account
                                 {:account-id (:account-id account)
                                  :status-after (:account-status account)})]
          account))))))

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
        [{:keys [accounts before after]} (store/get-accounts txn org-id opts)
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
  [txn org-id product-type]
  (store/get-account-by-type txn org-id product-type))

(defn get-account-by-bban
  [txn bban]
  (store/get-account-by-bban txn bban))

(defn close-account
  "Closes an account. opts supports `:policies` to override
  policy resolution."
  ([txn data]
   (close-account txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [organization-id account-id]} data]
        (let-nom>
          [policies (get-policies txn organization-id account-id opts)
           account (get-account txn organization-id account-id)
           updated (domain/close-account account policies)
           _ (store/save-account txn
                                 updated
                                 {:account-id account-id
                                  :status-before (:account-status account)
                                  :status-after (:account-status updated)})]
          updated))))))
