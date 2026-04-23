(ns com.repldriven.mono.bank-api.balance.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn list-balances
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [account-id]} (:path parameters)
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [_ (cash-accounts/get-account config
                                               organization-id
                                               account-id)
                  balances (balances/get-balances config account-id)]
                 balances)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn get-balance
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [account-id balance-type currency balance-status]} (:path
                                                                   parameters)
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [_ (cash-accounts/get-account config
                                               organization-id
                                               account-id)
                  balance (balances/get-balance config
                                                account-id
                                                balance-type
                                                currency
                                                balance-status)]
                 balance)]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          (nil? result)
          {:status 404
           :body (errors/error-response 404 "REJECTED"
                                        "balances/not-found"
                                        "Balance not found")}
          :else
          {:status 200 :body result})))

