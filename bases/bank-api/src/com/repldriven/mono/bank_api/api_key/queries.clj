(ns com.repldriven.mono.bank-api.api-key.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-api-key.interface :as bank-api-key]
    [com.repldriven.mono.error.interface :as error]))

(defn get-api-keys
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (bank-api-key/get-api-keys config org-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body {:api-keys (or result [])}})))
