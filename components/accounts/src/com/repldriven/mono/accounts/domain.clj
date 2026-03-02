(ns com.repldriven.mono.accounts.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-account
  "Creates a new account map with status open. Returns nil
  if customer-exists? is truthy."
  [customer-exists? data]
  (when-not customer-exists?
    (assoc data :account-id (encryption/generate-id "ba") :status "open")))

(defn set-status
  "Returns account with updated status."
  [account status]
  (assoc account :status status))
