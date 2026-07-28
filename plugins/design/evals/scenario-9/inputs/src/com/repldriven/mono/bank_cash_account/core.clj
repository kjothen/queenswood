(ns com.repldriven.mono.bank-cash-account.core
  (:require
    [com.repldriven.mono.bank-cash-account.store :as store]
    [com.repldriven.mono.error.interface :as error]))

(defn close-account
  [txn record-db account-id]
  (error/let-nom> [account (store/get-account txn record-db account-id)
                   _ (when-not (zero? (:balance account))
                       (error/reject :account/non-zero-balance
                                     {:message "balance must be zero to close"
                                      :account-id account-id}))]
    (store/save-account txn record-db (assoc account :status :closed))))
