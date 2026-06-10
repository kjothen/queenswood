(ns com.repldriven.mono.bank-bank.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "banks")

(def transact fdb/transact)

(defn allocate-sort-code
  "Allocate the next sort code from a global monotonic fountain, formatted
  as a 6-digit string (000001, 000002, ...). `00`-prefixed sort codes are
  unallocated in the real world, so the range is safe."
  [txn]
  (fdb/transact txn
                (fn [txn]
                  (format "%06d"
                          (fdb/allocate-counter (fdb/open txn store-name)
                                                "bank"
                                                "sort-codes")))
                :bank/allocate-sort-code
                "Failed to allocate sort code"))

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

(defn create
  [txn bank]
  (fdb/transact txn
                (fn [txn]
                  (fdb/save-record (fdb/open txn store-name)
                                   (schema/Bank->java bank)))
                :bank/create
                "Failed to create bank"))

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
