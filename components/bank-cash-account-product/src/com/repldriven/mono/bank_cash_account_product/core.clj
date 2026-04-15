(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "cash-account-product-versions")

(defn new-product
  "Creates a cash account product as an initial draft v1.
  Returns {:version <map>} or anomaly."
  [txn org-id version-data]
  (let [product-id (encryption/generate-id "prd")
        version (domain/new-version org-id product-id 1 version-data)]
    (fdb/transact txn
                  (fn [txn]
                    (let-nom>
                      [_ (fdb/save-record
                          (fdb/open txn store-name)
                          (schema/CashAccountProductVersion->java version))]
                      {:version version}))
                  :cash-account-product/create
                  "Failed to create product")))

(defn new-version
  "Creates a new draft version for a product. Computes
  next version-number from existing versions. Returns
  {:version <map>} or anomaly."
  [txn org-id product-id version-data]
  (fdb/transact txn
                (fn [txn]
                  (let [store (fdb/open txn store-name)
                        existing (fdb/scan-records
                                  store
                                  {:prefix [org-id product-id]
                                   :limit 1000})
                        records (:records existing)
                        latest (->> records
                                    (map schema/pb->CashAccountProductVersion)
                                    (sort-by :version-number)
                                    last)]
                    (if (and latest (= "draft" (:status latest)))
                      (error/reject
                       :cash-account-product/draft-exists
                       {:message (str "Cannot create a new version while the "
                                      "latest version is still a draft")})
                      (let [next-num (inc (count records))
                            version (domain/new-version org-id
                                                        product-id
                                                        next-num
                                                        version-data)]
                        (fdb/save-record
                         store
                         (schema/CashAccountProductVersion->java version))
                        {:version version}))))))

(defn get-version
  "Loads a version by org-id, product-id, and version-id.
  Returns the version map or rejection anomaly if not
  found."
  [txn org-id product-id version-id]
  (let-nom>
    [result (fdb/transact
             txn
             (fn [txn]
               (fdb/load-record (fdb/open txn store-name)
                                org-id
                                product-id
                                version-id)))]
    (if result
      (schema/pb->CashAccountProductVersion result)
      (error/reject :cash-account-product/version-not-found
                    {:message "Version not found"
                     :organization-id org-id
                     :product-id product-id
                     :version-id version-id}))))

(defn get-versions
  "Lists versions. With product-id, scans that product;
  without, scans all products for the org. Returns
  {:versions [<map> ...]} or anomaly."
  ([txn org-id]
   (let-nom>
     [result (fdb/transact
              txn
              (fn [txn]
                (fdb/scan-records (fdb/open txn store-name)
                                  {:prefix [org-id] :limit 1000})))]
     {:versions (mapv schema/pb->CashAccountProductVersion (:records result))}))
  ([txn org-id product-id]
   (let-nom>
     [result (fdb/transact
              txn
              (fn [txn]
                (fdb/scan-records (fdb/open txn store-name)
                                  {:prefix [org-id product-id] :limit 100})))]
     {:versions (mapv schema/pb->CashAccountProductVersion
                      (:records result))})))

(defn- load-published
  [store org-id product-id]
  (let [result (fdb/scan-records
                store
                {:prefix [org-id product-id]
                 :limit 1000})]
    (->> (:records result)
         (map schema/pb->CashAccountProductVersion)
         (filter (fn [v] (= "published" (:status v))))
         (sort-by :version-number)
         last)))

(defn get-published
  "Returns the highest-version-number published version
  for a product, or nil if none published."
  [txn org-id product-id]
  (fdb/transact txn
                (fn [txn]
                  (load-published (fdb/open txn store-name)
                                  org-id
                                  product-id))
                :cash-account-product/get-published
                "Failed to load published product version"))

(defn publish
  "Publishes a draft version. Returns the published
  version map or anomaly."
  [txn org-id product-id version-id]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)
           bytes (fdb/load-record store org-id product-id version-id)]
       (cond
        (nil? bytes)
        (error/reject :cash-account-product/version-not-found
                      {:message "Version not found"})

        :else
        (let [version (schema/pb->CashAccountProductVersion bytes)]
          (if-not (= "draft" (:status version))
            (error/reject :cash-account-product/not-draft
                          {:message "Only draft versions can be published"})
            (let [published (domain/publish version)]
              (fdb/save-record
               store
               (schema/CashAccountProductVersion->java published))
              published))))))
   :cash-account-product/publish
   "Failed to publish product version"))
