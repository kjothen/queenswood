(ns com.repldriven.mono.bank-api.party.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [parties]} dispatchers]
    parties))

(defn create-party
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters]
    (commands/send (dispatcher request)
                   request
                   "create-party"
                   "party"
                   (assoc body :bank-id bank-id))))
