(ns com.repldriven.mono.bank-bank.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "banks")

(def transact fdb/transact)

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

(defn count-banks-by-type
  [txn bank-type]
  (fdb/transact txn
                (fn [txn]
                  (fdb/count-records
                   (fdb/open txn store-name)
                   "Bank_count_by_type"
                   (.getNumber
                    (schema/bank-type->pb-enum bank-type))))
                :bank/count-by-type
                "Failed to count banks by type"))

(defn get-banks-by-type
  [txn bank-type]
  (fdb/transact txn
                (fn [txn]
                  (mapv schema/pb->Bank
                        (fdb/query-records
                         (fdb/open txn store-name)
                         "Bank"
                         "type"
                         (schema/bank-type->pb-enum bank-type)
                         {:index "Bank_by_type"})))
                :bank/list-by-type
                "Failed to list banks by type"))
