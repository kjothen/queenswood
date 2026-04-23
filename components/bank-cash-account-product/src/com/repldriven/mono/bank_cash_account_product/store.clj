(ns com.repldriven.mono.bank-cash-account-product.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]))

(def ^:private store-name "cash-account-product-versions")

(def transact fdb/transact)

(defn save-version
  "Saves a product version. Returns nil or anomaly."
  [txn version]
  ;; TEMP DEBUG — revert once the duplicates mystery is solved. Logs
  ;; every save with the version-id and balance-products so we can
  ;; see whether duplicates ever reach FDB (and, if so, what shape).
  (let [bps (:balance-products version)]
    (log/info (str "save-version"
                   " product-id=" (:product-id version)
                   " version-id=" (:version-id version)
                   " status=" (:status version)
                   " bp-count=" (count bps)
                   " bp-distinct?=" (or (empty? bps) (apply distinct? bps))
                   " bp=" (pr-str bps))))
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/CashAccountProductVersion->java version)))
   :cash-account-product/save-version
   "Failed to save product version"))

(defn get-version
  "Loads a version by composite PK. Returns the version
  map or rejection anomaly if not found."
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

  Returns a vector of version maps or anomaly. When
  :product-id is supplied, rejects if the scan returns
  no versions — every product is created with v1, so a
  product-id that resolves to zero versions is an unknown
  product."
  ([txn org-id]
   (get-versions txn org-id nil))
  ([txn org-id opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [product-id limit]} opts
            prefix (if product-id [org-id product-id] [org-id])
            versions
            (mapv schema/pb->CashAccountProductVersion
                  (:records (fdb/scan-records (fdb/open txn store-name)
                                              {:prefix prefix
                                               :limit (or limit 1000)})))]
        (if (and product-id (empty? versions))
          (error/reject :cash-account-product/not-found
                        {:message "Product not found"
                         :organization-id org-id
                         :product-id product-id})
          versions)))
    :cash-account-product/list-versions
    "Failed to list product versions")))
