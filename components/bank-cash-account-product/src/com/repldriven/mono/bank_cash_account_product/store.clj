(ns com.repldriven.mono.bank-cash-account-product.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "cash-account-product-versions")

(def transact fdb/transact)

(defn save-version
  "Saves a product version. Returns nil or anomaly."
  [txn version]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/CashAccountProductVersion->java version)))
   :cash-account-product/save-version
   "Failed to save product version"))

(defn get-version
  "Loads a version by composite PK. Returns the version map or
  rejection anomaly if not found."
  [txn org-id product-id version-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      org-id
                                      product-id
                                      version-id)]
       (schema/pb->CashAccountProductVersion record)
       (error/reject :cash-account-product/version-not-found
                     {:message "Version not found"
                      :organization-id org-id
                      :product-id product-id
                      :version-id version-id})))
   :cash-account-product/get-version
   "Failed to load product version"))

(defn get-versions
  "Scans product versions. opts supports:
    :product-id - restricts the scan to a single product
    :limit      - row cap (default 1000)
    :order      - `:asc` or `:desc` (default); `:desc` returns versions
                  newest-first by primary-key order — that's the shape
                  this component's callers want (clients display version
                  history newest-first)

  Returns a vector of version maps or anomaly. When :product-id is
  supplied, rejects with `:cash-account-product/product-not-found`
  if the scan returns no versions — every product is created with
  v1, so a product-id that resolves to zero versions is unknown."
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
            (mapv schema/pb->CashAccountProductVersion
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
