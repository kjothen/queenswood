(ns com.repldriven.mono.bank-payee-check.interface
  (:require
    [com.repldriven.mono.bank-payee-check.core :as core]))

(defn check-payee
  "Persists a payee check for an organization. Returns the
  check map or anomaly."
  [config organization-id request result]
  (core/check-payee config organization-id request result))

(defn get-check
  "Loads a payee check by org-id and check-id. Returns the
  check map or rejection anomaly if not found."
  [txn org-id check-id]
  (core/get-check txn org-id check-id))

(defn list-checks
  "Lists payee checks for an organization with pagination.
  Returns {:items [...] :before id|nil :after id|nil} or
  anomaly."
  ([txn org-id]
   (core/list-checks txn org-id))
  ([txn org-id opts]
   (core/list-checks txn org-id opts)))
