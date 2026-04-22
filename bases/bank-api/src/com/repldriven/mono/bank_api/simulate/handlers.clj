(ns com.repldriven.mono.bank-api.simulate.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher
  [request]
  (get-in request [:dispatchers :transactions]))

(defn- interest-dispatcher
  [request]
  (get-in request [:dispatchers :interest]))

(defn inbound-transfer
  [request]
  (let [{:keys [internal-account-id parameters]} request
        {:keys [body]} parameters
        {:keys [account-id amount currency]} body]
    (commands/send
     (dispatcher request)
     request
     "record-transaction"
     "transaction"
     {:idempotency-key (get-in request
                               [:headers "idempotency-key"])
      :transaction-type :transaction-type-internal-transfer
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
              :amount amount}]})))

(defn accrue
  [request]
  (let [{:keys [parameters]} request
        {:keys [path body]} parameters
        {:keys [org-id]} path
        {:keys [as-of-date]} body]
    (commands/send
     (interest-dispatcher request)
     request
     "accrue-daily-interest"
     "interest-result"
     {:idempotency-key (get-in request
                               [:headers "idempotency-key"])
      :organization-id org-id
      :as-of-date as-of-date})))

(defn capitalize
  [request]
  (let [{:keys [parameters]} request
        {:keys [path body]} parameters
        {:keys [org-id]} path
        {:keys [as-of-date]} body]
    (commands/send
     (interest-dispatcher request)
     request
     "capitalize-monthly-interest"
     "interest-result"
     {:idempotency-key (get-in request
                               [:headers "idempotency-key"])
      :organization-id org-id
      :as-of-date as-of-date})))
