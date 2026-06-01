(ns com.repldriven.mono.bank-api.simulate.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-bank.interface :as banks]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-ledger-account.interface :as
     ledger-accounts]
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
  (or
   (check-bank request)
   (let [{:keys [record-db record-store parameters]} request
         {:keys [path body]} parameters
         {:keys [bank-id]} path
         {:keys [account-id amount currency]} body
         suspense (ledger-accounts/find-by-code
                   {:record-db record-db :record-store record-store}
                   bank-id
                   "2500")]
     (if (or (nil? suspense) (error/anomaly? suspense))
       (errors/anomaly->response
        (error/fail :simulate/no-suspense-account
                    {:message
                     "Bank has no 2500 suspense account in its chart"
                     :bank-id bank-id}))
       (let [txn {:record-db record-db :record-store record-store}
             account (cash-accounts/get-account txn bank-id account-id)
             product-type (when (and (map? account)
                                     (not (error/anomaly? account)))
                            (:product-type account))
             legs [{:account-id (:ledger-account-id suspense)
                    :balance-type :balance-type-default
                    :balance-status :balance-status-posted
                    :side :leg-side-debit
                    :amount amount}
                   (cond-> {:account-id account-id
                            :balance-type :balance-type-default
                            :balance-status :balance-status-posted
                            :side :leg-side-credit
                            :amount amount}
                           product-type
                           (assoc :product-type product-type))]
             expanded-legs (ledger-accounts/expand-legs
                            txn
                            bank-id
                            legs)]
         (if (error/anomaly? expanded-legs)
           (errors/anomaly->response expanded-legs)
           (commands/send
            (dispatcher request)
            request
            "record-transaction"
            "transaction"
            {:transaction-type :transaction-type-inbound-transfer
             :currency currency
             :reference "Simulated inbound transfer"
             :legs expanded-legs})))))))

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
