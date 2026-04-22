(ns com.repldriven.mono.bank-api.cash-account-product.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error]))

(defn create-product
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        body (get-in request [:parameters :body])
        result (cash-account-products/new-product {:record-db record-db
                                                   :record-store record-store}
                                                  org-id
                                                  body)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201 :body (:version result)})))

(defn upsert-draft
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        body (get-in request [:parameters :body])
        result (cash-account-products/upsert-draft
                {:record-db record-db :record-store record-store}
                org-id
                product-id
                body)]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          :else
          {:status 200 :body (:version result)})))

(defn publish
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/publish
                {:record-db record-db :record-store record-store}
                org-id
                product-id)]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          :else
          {:status 200 :body result})))
