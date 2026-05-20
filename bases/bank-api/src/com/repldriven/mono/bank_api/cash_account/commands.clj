(ns com.repldriven.mono.bank-api.cash-account.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [cash-accounts]} dispatchers]
    cash-accounts))

(defn open-cash-account
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [body]} parameters]
    (commands/send (dispatcher request)
                   request
                   "open-cash-account"
                   "cash-account"
                   (assoc body :organization-id organization-id))))

(defn close-cash-account
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [path]} parameters
        {:keys [account-id]} path]
    (commands/send (dispatcher request)
                   request
                   "close-cash-account"
                   "cash-account"
                   {:organization-id organization-id
                    :account-id account-id})))
