(ns com.repldriven.mono.accounts.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.fdb.interface :as fdb]))

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

(defn- uk-scan-address
  [store]
  (let [sort-code "040004"
        account-number (format "%08d"
                               (fdb/allocate-counter store
                                                     "bank"
                                                     "counters"
                                                     sort-code))]
    {:scheme "uk.scan"
     :identifier {:scan {:sort-code sort-code
                         :account-number account-number}}}))

(defn- add-payment-addresses
  [store account]
  (assoc account :payment-addresses [(uk-scan-address store)]))

(def ^:private lifecycle-transitions {"opening" "opened" "closing" "closed"})

(defn transition-lifecyle
  "Returns account with next status, or nil if no
  transition applies."
  [store account]
  (when-let [next-status (lifecycle-transitions (:status account))]
    (cond->
     (update-account-status next-status account)

     (= next-status "opened")
     (as-> acc (add-payment-addresses store acc)))))
