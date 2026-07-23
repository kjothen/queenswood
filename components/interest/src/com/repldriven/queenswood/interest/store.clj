(ns com.repldriven.queenswood.interest.store
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private interest-runs-store-name "interest-runs")

(def transact fdb/transact)

(defn save-run
  [txn run]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn interest-runs-store-name)
                      (schema/InterestRun->java run)))
   :interest/save-run
   "Failed to save interest run"))

(defn count-by-org-business-day-per-kind
  [txn bank-id status business-day]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/count-records
      (fdb/open txn interest-runs-store-name)
      "InterestRun_count_by_bank_status_business_day"
      [bank-id (schema/interest-run-status->int status) business-day]))
   :interest/count-by-org-business-day-per-kind
   {:message "Failed to count interest runs by org/day/kind"
    :bank-id bank-id
    :business-day business-day}))
