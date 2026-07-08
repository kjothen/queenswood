(ns com.repldriven.mono.bank-api.balance.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account-query.interface :as cash-accounts]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn create-balance
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path body]} parameters
        {:keys [account-id]} path
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [account (cash-accounts/get-account config
                                                     bank-id
                                                     account-id)
                  _ (when (nil? account)
                      (error/reject :cash-account/not-found
                                    {:message "Cash account not found"
                                     :account-id account-id}))
                  balance (balances/new-balance
                           config
                           (assoc body
                                  :account-id account-id
                                  :product-type (:product-type account)))]
                 balance)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201 :body result})))
