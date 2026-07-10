(ns com.repldriven.mono.bank-bank-query.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

;; must match bank-bank.store/store-name — same FDB store
(def ^:private store-name "banks")

(def transact fdb/transact)

(defn get-bank
  [txn bank-id]
  (fdb/transact txn
                (fn [txn]
                  (if-let [record (fdb/load-record (fdb/open txn store-name)
                                                   bank-id)]
                    (schema/pb->Bank record)
                    (error/reject :bank/not-found
                                  {:message "Bank not found"
                                   :bank-id bank-id})))
                :bank/get
                "Failed to load bank"))

(defn get-bank-by-sort-code
  [txn sort-code]
  (fdb/transact txn
                (fn [txn]
                  (some-> (fdb/query-record (fdb/open txn store-name)
                                            "Bank"
                                            "sort_code"
                                            sort-code
                                            {:index "Bank_by_sort_code"})
                          schema/pb->Bank))
                :bank/get-by-sort-code
                "Failed to get bank by sort code"))

(defn get-banks
  ([txn]
   (get-banks txn nil))
  ([txn opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [limit order] :or {limit 100 order :desc}} opts]
        (mapv schema/pb->Bank
              (:records (fdb/scan-records
                         (fdb/open txn store-name)
                         {:limit limit :order order})))))
    :bank/list
    "Failed to list banks")))
