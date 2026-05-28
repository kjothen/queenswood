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
  ([txn bank-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn {:bank-id bank-id})))
  ([txn bank-id account-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn
                                      {:bank-id bank-id
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
  [txn bank-id product-type account-type currency]
  (let-nom>
    [total (store/count-by-org txn bank-id)
     subtotal (store/count-by-org-product-account-type-currency
               txn
               bank-id
               product-type
               account-type
               currency)]
    {:cash-account
     {#{:bank-id} total
      #{:bank-id :product-type :account-type :currency} subtotal}}))

(defn open-account
  ([txn data]
   (open-account txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [bank-id party-id product-id currency]} data]
        (let-nom>
          [policies (get-policies txn bank-id opts)
           party (parties/get-party txn bank-id party-id)
           product (products/get-product txn
                                         bank-id
                                         product-id)
           product-version (products/published-version product)
           aggregates (when product-version
                        (counts txn
                                bank-id
                                (:product-type product-version)
                                (party->account-type party)
                                currency))
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
  ([txn bank-id account-id]
   (get-account txn bank-id account-id nil))
  ([txn bank-id account-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [account (store/find-account txn bank-id account-id)
         account (or account
                     (error/reject :cash-account/not-found
                                   {:message "Account not found"
                                    :bank-id bank-id
                                    :account-id account-id}))]
        (enrich-account txn opts account))))))

(defn get-accounts
  ([txn bank-id]
   (get-accounts txn bank-id nil))
  ([txn bank-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [{:keys [accounts before after]} (store/get-accounts txn bank-id opts)
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
  [txn bank-id product-type]
  (store/get-account-by-type txn bank-id product-type))

(defn get-account-by-bban
  [txn bban]
  (store/get-account-by-bban txn bban))

(defn close-account
  ([txn data]
   (close-account txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [bank-id account-id]} data]
        (let-nom>
          [policies (get-policies txn bank-id account-id opts)
           account (get-account txn bank-id account-id)
           updated (domain/close-account account policies)
           _ (store/save-account txn
                                 updated
                                 {:account-id account-id
                                  :status-before (:account-status account)
                                  :status-after (:account-status updated)})]
          updated))))))
