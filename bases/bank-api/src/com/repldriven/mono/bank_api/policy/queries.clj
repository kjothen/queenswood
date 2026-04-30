(ns com.repldriven.mono.bank-api.policy.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-policy.interface :as policies]
    [com.repldriven.mono.error.interface :as error]))

(defn list-policies
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (policies/get-policies config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:policies (or (:items result) [])}})))

(defn get-policy
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        {:keys [policy-id]} (get-in request [:parameters :path])
        result (policies/get-policy config policy-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
