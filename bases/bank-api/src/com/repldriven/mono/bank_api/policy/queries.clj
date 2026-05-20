(ns com.repldriven.mono.bank-api.policy.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-policy.interface :as policies]
    [com.repldriven.mono.error.interface :as error]))

(defn list-policies
  [request]
  (let [{:keys [record-db record-store]} request
        config {:record-db record-db :record-store record-store}
        result (policies/get-policies config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:policies (or (:items result) [])}})))

(defn get-policy
  [request]
  (let [{:keys [record-db record-store parameters]} request
        {:keys [path]} parameters
        {:keys [policy-id]} path
        config {:record-db record-db :record-store record-store}
        result (policies/get-policy config policy-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
