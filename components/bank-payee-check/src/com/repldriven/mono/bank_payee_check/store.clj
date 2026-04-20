(ns com.repldriven.mono.bank-payee-check.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "payee-checks")

(def transact fdb/transact)

(defn save-check
  "Saves a payee check. Returns nil or anomaly."
  [txn check]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/PayeeCheck->java check)))
   :payee-check/save
   "Failed to save payee check"))

(defn get-check
  "Loads a payee check by composite PK. Returns the check
  map or rejection anomaly if not found."
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

(defn list-checks
  "Lists payee checks for an organization. Returns
  {:items [maps] :before id|nil :after id|nil} or anomaly.
  opts supports :after, :before, :limit."
  ([txn org-id]
   (list-checks txn org-id nil))
  ([txn org-id opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [result (fdb/scan-records
                    (fdb/open txn store-name)
                    (merge {:prefix [org-id] :limit 20}
                           (select-keys opts
                                        [:after :before :limit])))]
        {:items (mapv schema/pb->PayeeCheck (:records result))
         :before (:before result)
         :after (:after result)}))
    :payee-check/list
    "Failed to list payee checks")))
