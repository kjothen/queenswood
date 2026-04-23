(ns com.repldriven.mono.bank-api.simulate.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.error.interface :as error]))

(defn- dispatcher
  [request]
  (get-in request [:dispatchers :transactions]))

(defn- interest-dispatcher
  [request]
  (get-in request [:dispatchers :interest]))

(defn- check-org
  "Confirms the path's `{org-id}` resolves to a real organization.
  Returns nil when found, or the anomaly-response for `:organization/not-found`."
  [request]
  (let [{:keys [record-db record-store parameters]} request
        {:keys [org-id]} (:path parameters)
        result (organizations/get-organization
                {:record-db record-db :record-store record-store}
                org-id)]
    (when (error/anomaly? result)
      (errors/anomaly->response result))))

(defn inbound-transfer
  [request]
  (or (check-org request)
      (let [{:keys [internal-account-id parameters]} request
            {:keys [body]} parameters
            {:keys [account-id amount currency]} body]
        (commands/send
         (dispatcher request)
         request
         "record-transaction"
         "transaction"
         {:transaction-type :transaction-type-internal-transfer
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
  (or (check-org request)
      (let [{:keys [parameters]} request
            {:keys [path body]} parameters
            {:keys [org-id]} path
            {:keys [as-of-date]} body]
        (commands/send
         (interest-dispatcher request)
         request
         "accrue-daily-interest"
         "interest-result"
         {:organization-id org-id
          :as-of-date as-of-date}))))

(defn capitalize
  [request]
  (or (check-org request)
      (let [{:keys [parameters]} request
            {:keys [path body]} parameters
            {:keys [org-id]} path
            {:keys [as-of-date]} body]
        (commands/send
         (interest-dispatcher request)
         request
         "capitalize-monthly-interest"
         "interest-result"
         {:organization-id org-id
          :as-of-date as-of-date}))))
