(ns com.repldriven.mono.bank-api.tier.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-tier.interface :as tiers]))

(defn list-tiers
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (tiers/get-tiers config)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200 :body {:tiers result}})))

(defn get-tier
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        {:keys [tier-type]} (get-in request
                                    [:parameters :path])
        result (tiers/get-tier config tier-type)]
    (cond
     (error/rejection? result)
     {:status 404 :body (error-response 404 result)}
     (error/anomaly? result)
     {:status 500 :body (error-response 500 result)}
     :else
     {:status 200 :body result})))
