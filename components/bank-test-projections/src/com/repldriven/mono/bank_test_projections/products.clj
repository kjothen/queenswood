(ns com.repldriven.mono.bank-test-projections.products
  "Product-status projections for the scenario runner. Reads each
  tracked product from the real bank and reports its status as
  `:draft` or `:published`, keyed by model-prod-id. The model side
  reads the same shape from `:products` in the model state.

  These let the property test catch cases where the runner thinks
  a product was created/published but the real bank never did
  (or vice-versa) — divergences that would otherwise only surface
  later via failed `:add-account` calls."
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]))

(defn- status
  "Reads the latest status of `product-id` in `org-id` from the
  real bank. Returns `:published` if any version is published,
  `:draft` otherwise (or the keyword for an explicit other state),
  or `nil` if the product doesn't exist."
  [bank org-id product-id]
  (let [product (products/get-product bank org-id product-id)]
    (cond
     (nil? product)
     nil
     (some (fn [v]
             (= :cash-account-product-status-published (:status v)))
           (:versions product))
     :published
     :else
     :draft)))

(defn project-products
  "For each entry in `model->real`, reads its product from the real
  bank and reports `:draft` or `:published`. `model->real` is
  `{model-prod-id {:real-id <id> :org-real-id <org>}}`. Returns
  `{model-prod-id :draft|:published}`."
  [bank model->real]
  (->> model->real
       (map (fn [[model-id {:keys [real-id org-real-id]}]]
              [model-id (status bank org-real-id real-id)]))
       (into {})))

(defn project-model-products
  "Reads product statuses out of model state. Returns
  `{model-prod-id :draft|:published}`."
  [model-state]
  (update-vals (:products model-state) :status))
