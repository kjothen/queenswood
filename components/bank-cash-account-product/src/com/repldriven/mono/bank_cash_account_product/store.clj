(ns com.repldriven.mono.bank-cash-account-product.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "cash-account-products")

(def transact fdb/transact)

(defn save-version
  [txn version]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/CashAccountProduct->java version)))
   :cash-account-product/save-version
   "Failed to save product version"))

(defn get-version
  [txn org-id product-id version-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      org-id
                                      product-id
                                      version-id)]
       (schema/pb->CashAccountProduct record)
       (error/reject :cash-account-product/version-not-found
                     {:message "Version not found"
                      :organization-id org-id
                      :product-id product-id
                      :version-id version-id})))
   :cash-account-product/get-version
   "Failed to load product version"))

(defn count-by-org
  [txn org-id]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/count-groups (fdb/open txn store-name)
                       "CashAccountProduct_count_by_org"
                       [org-id]))
   :cash-account-product/count-by-org
   {:message "Failed to count products by org"
    :organization-id org-id}))

(defn get-versions
  ([txn org-id]
   (get-versions txn org-id nil))
  ([txn org-id opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [product-id limit order]
             :or {limit 1000 order :desc}}
            opts
            prefix (if product-id [org-id product-id] [org-id])
            versions
            (mapv schema/pb->CashAccountProduct
                  (:records (fdb/scan-records (fdb/open txn store-name)
                                              {:prefix prefix
                                               :limit limit
                                               :order order})))]
        (if (and product-id (empty? versions))
          (error/reject :cash-account-product/product-not-found
                        {:message "Product not found"
                         :organization-id org-id
                         :product-id product-id})
          versions)))
    :cash-account-product/list-versions
    "Failed to list product versions")))
