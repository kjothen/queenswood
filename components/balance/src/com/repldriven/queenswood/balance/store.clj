(ns com.repldriven.queenswood.balance.store
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.queenswood.fdb.interface :as fdb]))

;; must match bank-balance-query.store/store-name — same FDB store
(def ^:private store-name "balances")

(def transact fdb/transact)

(defn save-balance
  [txn balance]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/Balance->java balance)))
   :balance/save
   "Failed to save balance"))
