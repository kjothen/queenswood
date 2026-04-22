(ns com.repldriven.mono.bank-api.organization.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-organization.interface
     :as organizations]))

(defn create-organization
  [request]
  (let [{:keys [record-db record-store parameters]} request
        {:keys [body]} parameters
        {:keys [name status tier-id currencies]} body
        config {:record-db record-db :record-store record-store}
        result (organizations/new-organization
                config
                name
                :organization-type-customer
                status
                tier-id
                currencies)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201
       :body (assoc (:organization result)
                    :api-key-secret
                    (:key-secret result))})))
