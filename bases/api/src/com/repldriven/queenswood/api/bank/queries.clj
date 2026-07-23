(ns com.repldriven.queenswood.api.bank.queries
  (:require
    [com.repldriven.queenswood.api.errors :as errors]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.queenswood.bank-query.interface :as banks]))

(defn list-banks
  [request]
  (let [{:keys [record-db record-store]} request
        config {:record-db record-db :record-store record-store}
        result (banks/get-banks config)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200
       :body {:banks (or result [])}})))
