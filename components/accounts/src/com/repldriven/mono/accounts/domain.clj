(ns com.repldriven.mono.accounts.domain)

(defn new-account
  "Creates a new account map with status open."
  [account-id customer-id name currency]
  {:account-id account-id
   :customer-id customer-id
   :name name
   :currency currency
   :status "open"})

(defn set-status
  "Returns account with updated status."
  [account status]
  (assoc account :status status))
