(ns com.repldriven.mono.bank-api.tier.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-tier.interface :as tiers]))

(defn create-tier
  [request]
  (let [{:keys [record-db record-store parameters]} request
        {:keys [body]} parameters
        {:keys [name policies limits]} body
        config {:record-db record-db :record-store record-store}
        result (tiers/new-tier config name policies limits)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201 :body result})))

(defn replace-tier
  [request]
  (let [{:keys [record-db record-store parameters]} request
        {:keys [path body]} parameters
        {:keys [tier-id]} path
        {:keys [policies limits]} body
        config {:record-db record-db :record-store record-store}
        result (tiers/update-tier config
                                  tier-id
                                  policies
                                  limits)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
