(ns com.repldriven.mono.bank-test-projections.products
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]))

(defn- normalise-status
  [status]
  (case status
    :cash-account-product-status-draft :draft
    :cash-account-product-status-published :published
    :cash-account-product-status-discarded :discarded))

(defn- versions-from-aggregate
  [aggregate]
  (mapv (fn [v]
          {:status (normalise-status (:status v))
           :number (:version-number v)})
        (:versions aggregate)))

(defn project-products
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
  [model-state]
  (update-vals (:products model-state)
               (fn [prod] (vec (reverse (:versions prod))))))
