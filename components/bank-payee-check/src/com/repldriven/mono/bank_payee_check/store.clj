(ns com.repldriven.mono.bank-payee-check.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "payee-checks")

(def transact fdb/transact)

(defn save-check
  [txn check]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/PayeeCheck->java check)))
   :payee-check/save
   "Failed to save payee check"))

(defn get-check
  [txn org-id check-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      org-id
                                      check-id)]
       (schema/pb->PayeeCheck record)
       (error/reject :payee-check/not-found
                     {:message "Payee check not found"
                      :check-id check-id})))
   :payee-check/get
   "Failed to load payee check"))

(defn get-checks
  ([txn org-id]
   (get-checks txn org-id nil))
  ([txn org-id opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [after before limit order]
             :or {limit 20 order :desc}}
            opts
            result (fdb/scan-records
                    (fdb/open txn store-name)
                    {:prefix [org-id]
                     :after after
                     :before before
                     :limit limit
                     :order order})]
        {:items (mapv schema/pb->PayeeCheck (:records result))
         :before (:before result)
         :after (:after result)}))
    :payee-check/list
    "Failed to list payee checks")))
