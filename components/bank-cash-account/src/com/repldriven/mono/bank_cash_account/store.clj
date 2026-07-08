(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

;; must match bank-cash-account-query.store/store-name — same FDB store
(def ^:private store-name "cash-accounts")

(def transact fdb/transact)

(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-account
  [txn account changelog]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (let-nom>
         [_ (fdb/save-record store (schema/CashAccount->java account))
          _ (fdb/write-changelog
             store
             store-name
             (:account-id account)
             (schema/CashAccountChangelog->pb
              (assoc changelog
                     :bank-id
                     (:bank-id account))))]
         nil)))
   :cash-account/save
   "Failed to save account"))

(defn allocate-payment-address
  [txn counter]
  (fdb/transact txn
                (fn [txn]
                  (format "%08d"
                          (fdb/allocate-counter (fdb/open txn store-name)
                                                "bank"
                                                "counters"
                                                counter)))
                :cash-account/allocate-payment-address
                "Failed to allocate payment address"))
