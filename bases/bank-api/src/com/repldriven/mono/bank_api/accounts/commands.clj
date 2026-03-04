(ns com.repldriven.mono.bank-api.accounts.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn open-account
  [request]
  (commands/send request
                 "open-account"
                 "account"
                 (get-in request [:parameters :body])))

(defn close-account
  [request]
  (let [{:keys [account-id]} (get-in request [:parameters :path])]
    (commands/send request "close-account" "account" {:account-id account-id})))
