(ns com.repldriven.mono.accounts.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-account
  "Creates a new account map with status opening. Returns
  nil if customer-exists? is truthy."
  [customer-exists? data]
  (when-not customer-exists?
    (assoc data :account-id (encryption/generate-id "ba") :status "opening")))

(defn set-status
  "Returns account with updated status."
  [account status]
  (assoc account :status status))

(def ^:private transitions {"opening" "opened" "closing" "closed"})

(defn transition
  "Returns account with next status, or nil if no
  transition applies."
  [account]
  (when-let [next-status (transitions (:status account))]
    (assoc account :status next-status)))
