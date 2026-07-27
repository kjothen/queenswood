(ns com.repldriven.queenswood.cash-account.core
  (:require
    [com.repldriven.queenswood.cash-account.domain :as domain]
    [com.repldriven.queenswood.cash-account.store :as store]

    [com.repldriven.queenswood.balance.interface :as balances]
    [com.repldriven.queenswood.balance-query.interface :as balances-q]
    [com.repldriven.queenswood.cash-account-product-query.interface :as
     products]
    [com.repldriven.queenswood.cash-account-query.interface :as q]
    [com.repldriven.queenswood.party-query.interface :as parties]
    [com.repldriven.queenswood.policy.interface :as policy]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- get-policies
  ([txn bank-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn {:bank-id bank-id})))
  ([txn bank-id account-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn
                                      {:bank-id bank-id
                                       :account-id account-id}))))

(defn- counts
  [txn bank-id product-type account-type currency]
  (let-nom>
    [total (q/count-by-org txn bank-id)
     subtotal (q/count-by-org-product-account-type-currency
               txn
               bank-id
               product-type
               account-type
               currency)]
    {:cash-account
     {#{:bank-id} total
      #{:bank-id :product-type :account-type :currency} subtotal}}))

(defn- product-type-of
  [product-version]
  (:product-type product-version))

(defn- or-already-opened
  "On a uniqueness violation — a redelivered or retried
  open-cash-account command carrying an already-seen idempotency-key —
  read the existing account back and return it, so the caller gets the
  original resource instead of a duplicate account or a bare rejection.
  Any other value passes through unchanged."
  [txn data result]
  (if (and (store/uniqueness-violation? result)
           (:idempotency-key data))
    (let-nom> [existing (q/find-account-by-idempotency-key
                         txn
                         (:bank-id data)
                         (:idempotency-key data))]
      (or existing result))
    result))

(defn open-account
  ([txn data]
   (open-account txn data {}))
  ([txn data opts]
   (or-already-opened
    txn
    data
    (store/transact
     txn
     (fn [txn]
       (let [{:keys [bank-id party-id product-id currency]} data]
         (let-nom>
           [policies (get-policies txn bank-id opts)
            party (parties/get-party txn bank-id party-id)
            product (products/get-product txn bank-id product-id)
            product-version (products/active-version product (utility/today))
            aggregates (when product-version
                         (counts txn
                                 bank-id
                                 (product-type-of product-version)
                                 (domain/party->account-type party)
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
           account)))))))

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
           account (q/get-account txn bank-id account-id)
           balances (balances-q/list-balances txn account-id)
           updated (domain/close-account account balances policies)
           _ (store/save-account txn
                                 updated
                                 {:account-id account-id
                                  :status-before (:account-status account)
                                  :status-after (:account-status updated)})]
          updated))))))

(defn complete-status-transition
  "Second leg of the two-step open and close: `opening` becomes
  `opened`, `closing` becomes `closed`.

  Gated on the loaded account still sitting at the expected source
  status, and skips silently when it doesn't. Event redelivery and
  replay must be a no-op here, not a rejection — the transition having
  already happened is the normal case, not an error."
  [txn bank-id account-id status-after]
  (when (#{:cash-account-status-opening :cash-account-status-closing}
         status-after)
    (store/transact
     txn
     (fn [txn]
       (let-nom>
         [account (q/find-account txn bank-id account-id)]
         (when (and account (= status-after (:account-status account)))
           (let [transitioned
                 (case status-after
                   :cash-account-status-opening
                   (domain/opened-account account)

                   :cash-account-status-closing
                   (domain/closed-account account))]
             (store/save-account txn
                                 transitioned
                                 {:account-id account-id
                                  :status-before status-after
                                  :status-after (:account-status
                                                 transitioned)}))))))))

(defn suspend-account
  ([txn data]
   (suspend-account txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [bank-id account-id]} data]
        (let-nom>
          [policies (get-policies txn bank-id account-id opts)
           account (q/get-account txn bank-id account-id)
           updated (domain/suspend-account account policies)
           _ (store/save-account txn
                                 updated
                                 {:account-id account-id
                                  :status-before (:account-status account)
                                  :status-after (:account-status updated)})]
          updated))))))

(defn resume-account
  ([txn data]
   (resume-account txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [bank-id account-id]} data]
        (let-nom>
          [policies (get-policies txn bank-id account-id opts)
           account (q/get-account txn bank-id account-id)
           updated (domain/resume-account account policies)
           _ (store/save-account txn
                                 updated
                                 {:account-id account-id
                                  :status-before (:account-status account)
                                  :status-after (:account-status updated)})]
          updated))))))

(defn rotate-address
  ([txn data]
   (rotate-address txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [bank-id account-id]} data]
        (let-nom>
          [policies (get-policies txn bank-id account-id opts)
           account (q/get-account txn bank-id account-id)
           product-version (products/get-version txn
                                                 bank-id
                                                 (:product-id account)
                                                 (:version-id account))
           updated (domain/rotate-address account
                                          product-version
                                          (fn [counter]
                                            (store/allocate-payment-address
                                             txn
                                             counter))
                                          policies)
           _ (store/save-account txn
                                 updated
                                 {:account-id account-id
                                  :status-before (:account-status account)
                                  :status-after (:account-status updated)})]
          updated))))))
