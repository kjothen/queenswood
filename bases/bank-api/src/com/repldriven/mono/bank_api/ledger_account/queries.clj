(ns com.repldriven.mono.bank-api.ledger-account.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-ledger-account.interface :as ledger-accounts]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [clojure.set :as set]))

(defn- ->api
  "Present a stored LedgerAccount over the wire: the internal
  `:ledger-account-id` is exposed as `:account-id` so the resource
  speaks the same id key as the path parameter and the balance API."
  [account]
  (set/rename-keys account {:ledger-account-id :account-id}))

(defn list-ledger-accounts
  [request]
  (let [{:keys [record-db record-store auth]} request
        {:keys [bank-id]} auth
        config {:record-db record-db :record-store record-store}
        result (ledger-accounts/list-accounts config bank-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:ledger-accounts (mapv ->api result)}})))

(defn get-ledger-account
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [account-id]} path
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [account (ledger-accounts/get-account config
                                                       bank-id
                                                       account-id)
                  _ (when (nil? account)
                      (error/reject :ledger-account/not-found
                                    {:message "Ledger account not found"
                                     :account-id account-id}))]
                 (->api account))]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn list-balances
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [account-id]} path
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [account (ledger-accounts/get-account config
                                                       bank-id
                                                       account-id)
                  _ (when (nil? account)
                      (error/reject :ledger-account/not-found
                                    {:message "Ledger account not found"
                                     :account-id account-id}))
                  balances (balances/get-balances config account-id)]
                 balances)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))