(ns com.repldriven.mono.bank-api.tier.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-tier.interface :as tiers]))

(defn list-tiers
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (tiers/get-tiers config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:tiers (or result [])}})))

(defn get-tier
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        {:keys [tier-id]} (get-in request [:parameters :path])
        result (tiers/get-tier config tier-id)]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          :else
          {:status 200 :body result})))
