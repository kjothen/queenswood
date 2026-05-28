(ns com.repldriven.mono.bank-api.simulate.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-bank.interface :as banks]
    [com.repldriven.mono.error.interface :as error]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [transactions]} dispatchers]
    transactions))

(defn- interest-dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [interest]} dispatchers]
    interest))

(defn- check-bank
  "Confirms the path's `{bank-id}` resolves to a real bank.
  Returns nil when found, or the anomaly-response for `:bank/not-found`."
  [request]
  (let [{:keys [record-db record-store parameters]} request
        {:keys [path]} parameters
        {:keys [bank-id]} path
        result (banks/get-bank
                {:record-db record-db :record-store record-store}
                bank-id)]
    (when (error/anomaly? result)
      (errors/anomaly->response result))))

(defn inbound-transfer
  [request]
  (or (check-bank request)
      (let [{:keys [internal-account-id parameters]} request
            {:keys [body]} parameters
            {:keys [account-id amount currency]} body]
        (commands/send
         (dispatcher request)
         request
         "record-transaction"
         "transaction"
         {:transaction-type :transaction-type-inbound-transfer
          :currency currency
          :reference "Simulated inbound transfer"
          :legs [{:account-id internal-account-id
                  :balance-type :balance-type-suspense
                  :balance-status :balance-status-posted
                  :side :leg-side-debit
                  :amount amount}
                 {:account-id account-id
                  :balance-type :balance-type-default
                  :balance-status :balance-status-posted
                  :side :leg-side-credit
                  :amount amount}]}))))

(defn accrue
  [request]
  (or (check-bank request)
      (let [{:keys [parameters]} request
            {:keys [path body]} parameters
            {:keys [bank-id]} path
            {:keys [as-of-date]} body]
        (commands/send
         (interest-dispatcher request)
         request
         "accrue-daily-interest"
         "interest-result"
         {:bank-id bank-id
          :as-of-date as-of-date}))))

(defn capitalize
  [request]
  (or (check-bank request)
      (let [{:keys [parameters]} request
            {:keys [path body]} parameters
            {:keys [bank-id]} path
            {:keys [as-of-date]} body]
        (commands/send
         (interest-dispatcher request)
         request
         "capitalize-monthly-interest"
         "interest-result"
         {:bank-id bank-id
          :as-of-date as-of-date}))))
