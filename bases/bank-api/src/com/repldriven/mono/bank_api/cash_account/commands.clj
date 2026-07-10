(ns com.repldriven.mono.bank-api.cash-account.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]

    [com.repldriven.mono.bank-bank-query.interface :as banks]
    [com.repldriven.mono.error.interface :as error]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [cash-accounts]} dispatchers]
    cash-accounts))

(defn open-cash-account
  [request]
  (let [{:keys [auth parameters record-db record-store]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters
        ;; The bank's sort code prefixes its accounts' BBANs; resolve it
        ;; here (above cash-account, which can't depend on bank-bank) and
        ;; pass it on the open command.
        bank (banks/get-bank {:record-db record-db :record-store record-store}
                             bank-id)]
    (if (error/anomaly? bank)
      bank
      (commands/send (dispatcher request)
                     request
                     "open-cash-account"
                     "cash-account"
                     (assoc body
                            :bank-id bank-id
                            :sort-code (:sort-code bank))))))

(defn close-cash-account
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [account-id]} path]
    (commands/send (dispatcher request)
                   request
                   "close-cash-account"
                   "cash-account"
                   {:bank-id bank-id
                    :account-id account-id})))
