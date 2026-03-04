(ns com.repldriven.mono.accounts.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn open-account
  "Creates a new account map with status opening.
  existing-accounts is the list of accounts the customer
  already has (unused for now)."
  [data _existing-accounts]
  (let [now (System/currentTimeMillis)]
    (assoc data
           :account-id (encryption/generate-id "ba")
           :status "opening"
           :created-at-ms now
           :updated-at-ms now)))

(defn update-account-status
  "Returns account with updated status and timestamp."
  [status account]
  (assoc account :status status :updated-at-ms (System/currentTimeMillis)))

(defn close-account
  "Returns account with status closing."
  [account]
  (update-account-status "closing" account))

(def ^:private lifecycle-transitions {"opening" "opened" "closing" "closed"})

(defn transition-lifecyle
  "Returns account with next status, or nil if no
  transition applies."
  [account]
  (when-let [next-status (lifecycle-transitions (:status account))]
    (update-account-status next-status account)))
