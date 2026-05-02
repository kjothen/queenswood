(ns com.repldriven.mono.bank-test-projections.products
  "Product version-history projection. Each tracked product is
  projected as its sequence of `{:status :draft|:published|
  :discarded :number n}` versions, sorted by `:number` ascending,
  letting the property test catch any drift between model intent
  and real-side reality — including out-of-order publishes, missed
  drafts, or stale discarded versions.

  Reads through `bank-cash-account-product/get-product`, the same
  query path the API uses."
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]))

(defn- normalise-status
  "Maps the real `:cash-account-product-status-*` enum to the
  model's three-state `:draft` / `:published` / `:discarded`."
  [status]
  (case status
    :cash-account-product-status-draft :draft
    :cash-account-product-status-published :published
    :cash-account-product-status-discarded :discarded))

(defn- versions-from-real
  "Reads versions from the real bank and reduces each to the
  model's projection shape. Sorted ascending by version-number so
  the projection has a canonical order regardless of the store's
  scan direction."
  [bank org-id product-id]
  (let [product (products/get-product bank org-id product-id)]
    (->> (:versions product)
         (mapv (fn [v]
                 {:status (normalise-status (:status v))
                  :number (:version-number v)}))
         (sort-by :number)
         vec)))

(defn project-products
  "For each entry in `model->real`, reads the product from the
  real bank and reports its versions list. `model->real` is
  `{model-prod-id {:real-id <id> :org-real-id <org>}}`. Returns
  `{model-prod-id [{:status ... :number n} ...]}`."
  [bank model->real]
  (->> model->real
       (map (fn [[model-id {:keys [real-id org-real-id]}]]
              [model-id (versions-from-real bank org-real-id real-id)]))
       (into {})))

(defn project-model-products
  "Reads the same shape from model state. Versions are stored in
  insertion order (which matches `:number` ascending) so no
  re-sorting is needed."
  [model-state]
  (update-vals (:products model-state)
               (fn [prod] (vec (:versions prod)))))
