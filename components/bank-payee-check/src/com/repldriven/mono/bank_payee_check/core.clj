(ns com.repldriven.mono.bank-payee-check.core
  (:require
    [com.repldriven.mono.bank-payee-check.domain :as domain]
    [com.repldriven.mono.bank-payee-check.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn check-payee
  [config organization-id request result]
  (let [check (domain/new-check organization-id request result)]
    (let-nom> [_ (store/save-check config check)]
      check)))

(defn get-check
  [txn org-id check-id]
  (store/get-check txn org-id check-id))

(defn get-checks
  ([txn org-id]
   (store/get-checks txn org-id))
  ([txn org-id opts]
   (store/get-checks txn org-id opts)))
