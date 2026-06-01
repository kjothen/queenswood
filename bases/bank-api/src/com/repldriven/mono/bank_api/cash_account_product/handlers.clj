(ns com.repldriven.mono.bank-api.cash-account-product.handlers
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.coercion :as coercion]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error]))

(defn- version-uri
  [{:keys [product-id version-id]}]
  (str "/v1/cash-account-products/" product-id "/versions/" version-id))

(defn create-product
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters
        result (cash-account-products/new-product
                {:record-db record-db :record-store record-store}
                bank-id
                (coercion/request->sub-ledger body))]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201
       :headers {"Location" (version-uri result)}
       :body (coercion/version->response result)})))

(defn open-draft
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path body]} parameters
        {:keys [product-id]} path
        result (cash-account-products/open-draft
                {:record-db record-db :record-store record-store}
                bank-id
                product-id
                (coercion/request->sub-ledger body))]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201
       :headers {"Location" (version-uri result)}
       :body (coercion/version->response result)})))

(defn update-draft
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path body]} parameters
        {:keys [product-id version-id]} path
        result (cash-account-products/update-draft
                {:record-db record-db :record-store record-store}
                bank-id
                product-id
                version-id
                (coercion/request->sub-ledger body))]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body (coercion/version->response result)})))

(defn discard-draft
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [product-id version-id]} path
        result (cash-account-products/discard-draft
                {:record-db record-db :record-store record-store}
                bank-id
                product-id
                version-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 204})))

(defn publish-draft
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [product-id version-id]} path
        result (cash-account-products/publish
                {:record-db record-db :record-store record-store}
                bank-id
                product-id
                version-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body (coercion/version->response result)})))
