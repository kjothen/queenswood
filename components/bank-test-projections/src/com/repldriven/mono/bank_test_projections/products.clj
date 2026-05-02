(ns com.repldriven.mono.bank-test-projections.products
  "Product version-history projection. For each tracked product
  emits the full sequence of `{:status :draft|:published|
  :discarded :number n}` versions in descending order — matching
  the real bank's `get-products` scan and pinning that contract
  on every trial. The model side reverses its (ascending)
  insertion order to align.

  Reads through `bank-cash-account-product/get-products` (one
  call per org), so the property test exercises that read path
  comprehensively in addition to per-product `get-product`."
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

(defn- versions-from-aggregate
  "Reduces a real-side product aggregate's `:versions` to the
  projection shape, preserving the order returned by the API
  (descending by version-number)."
  [aggregate]
  (mapv (fn [v]
          {:status (normalise-status (:status v))
           :number (:version-number v)})
        (:versions aggregate)))

(defn project-products
  "Reads each org's products via `get-products` (one call per
  unique org in `model->real`), then matches each tracked
  product-id and emits its versions list. Returns
  `{model-prod-id [{:status ... :number n} ...]}` — order is
  descending by `:number` to mirror the real API.

  `model->real` is `{model-prod-id {:real-id <id>
                                     :org-real-id <org>}}`."
  [bank model->real]
  (let [by-org (group-by (fn [[_ entry]] (:org-real-id entry))
                         model->real)]
    (->> by-org
         (mapcat (fn [[org-real-id entries]]
                   (let [{:keys [items]} (products/get-products bank
                                                                org-real-id)
                         items-by-id (into {}
                                           (map (juxt :product-id identity))
                                           items)]
                     (map (fn [[model-id {:keys [real-id]}]]
                            [model-id
                             (versions-from-aggregate
                              (get items-by-id real-id))])
                          entries))))
         (into {}))))

(defn project-model-products
  "Reads the same shape from model state. Versions are stored in
  insertion order (which matches `:number` ascending), so reverse
  to produce descending output that matches the real projection."
  [model-state]
  (update-vals (:products model-state)
               (fn [prod] (vec (reverse (:versions prod))))))
