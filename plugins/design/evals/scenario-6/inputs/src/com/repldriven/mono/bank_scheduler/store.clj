(ns com.repldriven.mono.bank-scheduler.store)

(defn get-job
  [txn record-db job-id]
  {:job-id job-id :enabled? true})

(defn save-job
  [txn record-db job]
  job)
