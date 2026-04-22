(ns com.repldriven.mono.bank-api.organization.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-organization.interface :as organizations]))

(defn list-organizations
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (organizations/get-organizations config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200
       :body {:organizations (or result [])}})))
