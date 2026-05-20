(ns com.repldriven.mono.bank-api.organization.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-organization.interface
     :as organizations]))

(defn create-organization
  [request]
  (let [{:keys [record-db record-store identity-provider
                audiences-by-status parameters]}
        request
        {:keys [body]} parameters
        {:keys [name status tier currencies]} body
        config {:record-db record-db :record-store record-store}
        ;; `audiences-by-status` is bank-api deployment config (sits
        ;; in server/interceptors next to `expected-audiences`). The
        ;; substrate IDP brick is naive about audience naming; the
        ;; handler resolves the per-status audience here and forwards
        ;; it through.
        audience (get audiences-by-status status)
        result (organizations/new-organization
                config
                name
                :organization-type-customer
                status
                tier
                currencies
                {:identity-provider identity-provider
                 :audience audience})
        {:keys [organization client-secret]} result]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201
       :body (assoc organization :client-secret client-secret)})))
