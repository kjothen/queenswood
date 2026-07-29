(ns com.repldriven.queenswood.api.payee-check.queries
  (:require
    [com.repldriven.queenswood.api.cursor :as cursor]
    [com.repldriven.queenswood.api.errors :as errors]

    [com.repldriven.queenswood.payee-check.interface :as payee-checks]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as utility]))

(defn get-check
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [check-id]} path
        config {:record-db record-db :record-store record-store}
        result (payee-checks/get-check config
                                       bank-id
                                       check-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn list-checks
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [query]} parameters
        {:keys [page]} query
        {:keys [after before size]} page
        after-id (cursor/decode after)
        before-id (cursor/decode before)
        size (cursor/clamp-size size)
        config {:record-db record-db :record-store record-store}
        result (payee-checks/get-checks config
                                        bank-id
                                        {:after after-id
                                         :before before-id
                                         :limit size})]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      (let [{:keys [items before after]} result
            links (when (seq items)
                    (cursor/build-links "/v1/payee-checks"
                                        size
                                        (when after-id before)
                                        after))]
        {:status 200
         :body (utility/assoc-seq {:items items} :links links)}))))
