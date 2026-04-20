(ns com.repldriven.mono.bank-payee-check.core
  (:require
    [com.repldriven.mono.bank-payee-check.domain :as domain]
    [com.repldriven.mono.bank-payee-check.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn check-payee
  "Persists a payee check for an organization. Returns the
  check map or anomaly."
  [config organization-id request result]
  (let [check (domain/new-check organization-id request result)]
    (let-nom> [_ (store/save-check config check)]
      check)))

(defn get-check
  "Loads a payee check by org-id and check-id."
  [txn org-id check-id]
  (store/get-check txn org-id check-id))

(defn list-checks
  "Lists payee checks for an organization with pagination."
  ([txn org-id]
   (store/list-checks txn org-id))
  ([txn org-id opts]
   (store/list-checks txn org-id opts)))
