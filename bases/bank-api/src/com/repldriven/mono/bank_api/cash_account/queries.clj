(ns com.repldriven.mono.bank-api.cash-account.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-page-size 20)

(defn- build-links
  [base before-id after-id]
  (cond-> {}
          after-id
          (assoc :next
                 (str base
                      "?page[after]="
                      (cursor/encode after-id)))
          before-id
          (assoc :prev
                 (str base
                      "?page[before]="
                      (cursor/encode before-id)))))

(defn list-cash-accounts
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        query (get-in request [:parameters :query])
        {:keys [page embed]} query
        after-id (cursor/decode (:after page))
        before-id (cursor/decode (:before page))
        size (or (:size page) default-page-size)
        embed-balances (:balances embed)
        embed-transactions (:transactions embed)
        opts (cond->
              {:limit size}

              after-id
              (assoc :after after-id)

              before-id
              (assoc :before before-id)

              (some? embed-balances)
              (assoc :embed-balances embed-balances)

              (some? embed-transactions)
              (assoc :embed-transactions embed-transactions))
        result (cash-accounts/get-accounts
                {:record-db record-db
                 :record-store record-store}
                org-id
                opts)]

    (if (error/anomaly? result)
      (errors/anomaly->response result)
      (let [{:keys [accounts before after]} result
            links (when (seq accounts)
                    (build-links "/v1/cash-accounts"
                                 (when after-id before)
                                 after))]
        {:status 200
         :body (cond->
                {:cash-accounts accounts}

                (seq links)
                (assoc :links links))}))))

(defn get-cash-account
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [account-id]} (get-in request [:parameters :path])
        query (get-in request [:parameters :query])
        {:keys [embed]} query
        embed-balances (:balances embed)
        embed-transactions (:transactions embed)
        result (cash-accounts/get-account
                {:record-db record-db :record-store record-store}
                org-id
                account-id
                (cond-> {}
                        (some? embed-balances)
                        (assoc :embed-balances embed-balances)
                        (some? embed-transactions)
                        (assoc :embed-transactions embed-transactions)))]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          (nil? result)
          {:status 404
           :body (errors/error-response 404 "REJECTED"
                                        "cash-accounts/not-found"
                                        "Cash account not found")}
          :else
          {:status 200 :body result})))

(defn list-transactions
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [account-id]} (:path parameters)
        config {:record-db record-db :record-store record-store}
        result (error/let-nom>
                 [_ (cash-accounts/get-account config
                                               organization-id
                                               account-id)
                  txns (transactions/get-transactions config account-id)]
                 txns)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:transactions (or result [])}})))
