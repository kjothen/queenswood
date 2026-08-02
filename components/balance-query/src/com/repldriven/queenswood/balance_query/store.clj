(ns com.repldriven.queenswood.balance-query.store
  (:require
    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

;; must match bank-balance.store/store-name — same FDB store. Public
;; because a caller pairing accounts with balances in one scan has to
;; name this store to `fdb/merge-scan`, and a third hardcoded copy of
;; the string is worse than saying where the one copy lives.
(def store-name "balances")

(def transact fdb/transact)

(defn find-balance
  [txn account-id balance-type currency balance-status]
  (let-nom>
    [result (fdb/transact
             txn
             (fn [txn]
               (fdb/load-record
                (fdb/open txn store-name)
                account-id
                (schema/balance-type->int balance-type)
                currency
                (schema/balance-status->int balance-status)))
             :balance/find
             "Failed to load balance")]
    (when result (schema/pb->Balance result))))

(defn get-balance
  [txn account-id balance-type currency balance-status]
  (let-nom>
    [balance (find-balance txn
                           account-id
                           balance-type
                           currency
                           balance-status)]
    (or balance
        (error/reject :balance/not-found
                      {:message "Balance not found"
                       :account-id account-id
                       :balance-type balance-type
                       :currency currency
                       :balance-status balance-status}))))

(defn get-balances
  [txn account-id]
  (fdb/transact txn
                (fn [txn]
                  (mapv schema/pb->Balance
                        (:records (fdb/scan-records
                                   (fdb/open txn store-name)
                                   {:prefix [account-id]
                                    :limit 100}))))
                :balance/list
                "Failed to list balances"))
