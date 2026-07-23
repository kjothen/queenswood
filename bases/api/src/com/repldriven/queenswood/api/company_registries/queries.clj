(ns com.repldriven.queenswood.api.company-registries.queries
  (:require
    [com.repldriven.queenswood.api.errors :as errors]
    [com.repldriven.queenswood.company-registry.interface :as company-registry]

    [com.repldriven.mono.error.interface :as error]))

(defn lookup-company
  [request]
  (let [{:keys [registry-id company-number]} (get-in request
                                                     [:parameters :path])
        config (select-keys request
                            [:companies-house-url :record-db :record-store])
        result (company-registry/lookup-company config
                                                registry-id
                                                company-number)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
