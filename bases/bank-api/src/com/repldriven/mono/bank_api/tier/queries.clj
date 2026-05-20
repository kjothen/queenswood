(ns com.repldriven.mono.bank-api.tier.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-policy.interface :as policies]

    [com.repldriven.mono.error.interface :as error]))

(defn list-tiers
  [request]
  (let [{:keys [record-db record-store]} request
        config {:record-db record-db :record-store record-store}
        result (policies/get-tiers config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:tiers (or result [])}})))
