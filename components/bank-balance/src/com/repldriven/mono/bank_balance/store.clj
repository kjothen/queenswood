(ns com.repldriven.mono.bank-balance.store
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "balances")

(def transact fdb/transact)

(defn save-balance
  "Persists a balance. Returns nil or anomaly."
  [txn balance]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/Balance->java balance)))
   :balance/save
   "Failed to save balance"))

(defn find-balance
  "Loads a balance by its composite primary key if it
  exists. Returns the balance map, nil (if none), or
  anomaly on I/O failure. For existence probes."
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
  "Loads a balance by its composite primary key. Returns
  the balance map or a rejection anomaly if not found."
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
  "Lists balances for an account. Returns a sequence of
  balances or anomaly."
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
