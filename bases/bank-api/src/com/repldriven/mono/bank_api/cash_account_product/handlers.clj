(ns com.repldriven.mono.bank-api.cash-account-product.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error]))

(defn- version-uri
  [{:keys [product-id version-id]}]
  (str "/v1/cash-account-products/" product-id "/versions/" version-id))

(defn create-product
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        body (get-in request [:parameters :body])
        result (cash-account-products/new-product
                {:record-db record-db :record-store record-store}
                org-id
                body)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201
       :headers {"Location" (version-uri result)}
       :body result})))

(defn open-draft
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        body (get-in request [:parameters :body])
        result (cash-account-products/open-draft
                {:record-db record-db :record-store record-store}
                org-id
                product-id
                body)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201
       :headers {"Location" (version-uri result)}
       :body result})))

(defn update-draft
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        body (get-in request [:parameters :body])
        result (cash-account-products/update-draft
                {:record-db record-db :record-store record-store}
                org-id
                product-id
                version-id
                body)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn discard-draft
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        result (cash-account-products/discard-draft
                {:record-db record-db :record-store record-store}
                org-id
                product-id
                version-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 204})))

(defn publish-draft
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        result (cash-account-products/publish
                {:record-db record-db :record-store record-store}
                org-id
                product-id
                version-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
