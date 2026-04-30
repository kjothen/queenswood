(ns com.repldriven.mono.bank-api.tier.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-policy.interface :as policies]
    [com.repldriven.mono.error.interface :as error]))

(defn list-tiers
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (policies/get-tiers config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:tiers (or result [])}})))
