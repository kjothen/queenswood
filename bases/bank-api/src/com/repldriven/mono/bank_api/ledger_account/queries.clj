(ns com.repldriven.mono.bank-api.ledger-account.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-ledger-account.interface :as ledger-accounts]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [clojure.set :as set]))

(defn- ->api
  "Present a stored LedgerAccount over the wire: the internal
  `:ledger-account-id` is exposed as `:account-id` so the resource speaks
  the same id key as the path parameter and the balance API, and the
  `:gl-account-code` role is rendered back to its chart number as the
  `:gl-code` string clients (and the console's ledger view) expect."
  [account]
  (-> account
      (set/rename-keys {:ledger-account-id :account-id})
      (assoc :gl-code
             (ledger-accounts/gl-account-code->gl-code
              (:gl-account-code account)))
      (dissoc :gl-account-code)))

(defn- with-posted-balances
  "Attach each ledger account's derived `:posted-balance` ({value,
  currency}) — the same per-account read the balances endpoint does,
  batched server-side so the list carries the headline figure and the
  trial balance can be summed here rather than in the client. Returns the
  enriched accounts, or the first balance anomaly."
  [config accounts]
  (reduce (fn [acc account]
            (let [balances (balances/get-balances
                            config
                            (:ledger-account-id account))]
              (if (error/anomaly? balances)
                (reduced balances)
                (conj
                 acc
                 (assoc account :posted-balance (:posted-balance balances))))))
          []
          accounts))

(defn- trial-balance-entry
  "Project an enriched account into a bank-balance trial-balance entry:
  its currency, normal side (from the gl-account-type), and posted net."
  [account]
  {:currency (:currency account)
   :normal-side (if (ledger-accounts/debit-normal? (:gl-account-type account))
                  :debit
                  :credit)
   :value (:value (:posted-balance account))})

(defn list-ledger-accounts
  [request]
  (let [{:keys [record-db record-store auth]} request
        {:keys [bank-id]} auth
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [accounts (ledger-accounts/list-accounts config bank-id)
                  enriched (with-posted-balances config accounts)]
                 {:ledger-accounts (mapv ->api enriched)
                  :trial-balance (balances/trial-balance
                                  (map trial-balance-entry enriched))})]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

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