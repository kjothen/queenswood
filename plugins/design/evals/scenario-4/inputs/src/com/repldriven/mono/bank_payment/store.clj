(ns com.repldriven.mono.bank-payment.store)

(defn update-status
  [txn record-db transaction-id status]
  ;; persists the transaction's new status
  {:transaction-id transaction-id :status status})
