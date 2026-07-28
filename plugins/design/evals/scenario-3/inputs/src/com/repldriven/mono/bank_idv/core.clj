(ns com.repldriven.mono.bank-idv.core
  (:require
    [com.repldriven.mono.bank-idv.store :as store]))

(defn complete-check
  [txn record-db check-id result]
  (store/save-check txn
                    record-db
                    (assoc result
                           :check-id check-id
                           :status :complete)))
