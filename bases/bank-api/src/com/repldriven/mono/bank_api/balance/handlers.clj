(ns com.repldriven.mono.bank-api.balance.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn create-balance
  [request]
  (let [{:keys [record-db record-store auth]} request
        {:keys [organization-id]} auth
        account-id (get-in request [:parameters :path :account-id])
        body (get-in request [:parameters :body])
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [account (cash-accounts/get-account config
                                                     organization-id
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
