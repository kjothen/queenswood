(ns com.repldriven.mono.bank-api.cash-account-product.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error]))

(defn list-products
  "GET /cash-account-products — returns the latest version
  per product in the organisation."
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        result (cash-account-products/get-products request org-id)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200 :body {:versions (:versions result)}})))

(defn get-latest-version
  "GET /cash-account-products/{product-id} — returns the
  latest version (draft or published) for a product."
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-latest-version request
                                                         org-id
                                                         product-id)]
    (cond (error/anomaly? result)
          (if (= :cash-account-product/not-found (error/kind result))
            {:status 404 :body (error-response 404 result)}
            {:status 500 :body (error-response 500 result)})
          :else
          {:status 200 :body result})))

(defn get-published-version
  "GET /cash-account-products/{product-id}/published — returns
  the current published version."
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-published-version request
                                                            org-id
                                                            product-id)]
    (cond (error/anomaly? result)
          (if (= :cash-account-product/not-found (error/kind result))
            {:status 404 :body (error-response 404 result)}
            {:status 500 :body (error-response 500 result)})
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "cash-account-products/no-published-version"
                  "No published version found")}
          :else
          {:status 200 :body result})))

(defn list-versions
  "GET /cash-account-products/{product-id}/versions — all
  versions for a product."
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-versions request org-id product-id)]
    (cond (error/anomaly? result)
          (if (= :cash-account-product/not-found (error/kind result))
            {:status 404 :body (error-response 404 result)}
            {:status 500 :body (error-response 500 result)})
          :else
          {:status 200 :body {:versions (:versions result)}})))

(defn get-version
  "GET /cash-account-products/{product-id}/versions/{version-id}
  — specific version lookup."
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-version request
                                                  org-id
                                                  product-id
                                                  version-id)]
    (cond (error/anomaly? result)
          (if (= :cash-account-product/version-not-found (error/kind result))
            {:status 404 :body (error-response 404 result)}
            {:status 500 :body (error-response 500 result)})
          :else
          {:status 200 :body result})))
